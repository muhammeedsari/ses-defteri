package app.sesdefteri;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import app.sesdefteri.dsp.Resampler;

import com.k2fsa.sherpa.onnx.*;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 3.4 text to speech. "phone": the phone's own voice (Android TextToSpeech, nothing to download).
 * "ai": a natural Piper voice per language (sherpa-onnx VITS, ≈21 MB, downloaded once from GitHub as .tar.bz2 and
 * unpacked on the phone; the espeak-ng pronunciation data is shared by all voices). The result is a project clip.
 */
final class Tts {
    private Tts() { }

    static final String GH = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-";
    static final String[] LANGS = { "tr", "en", "de", "fr", "es", "pt", "ru", "ar", "zh" };

    static String voice(String lang) {
        switch (lang) {
            case "tr": return "tr_TR-dfki-medium-int8";
            case "de": return "de_DE-thorsten-medium-int8";
            case "fr": return "fr_FR-siwis-medium-int8";
            case "es": return "es_ES-davefx-medium-int8";
            case "pt": return "pt_BR-faber-medium-int8";
            case "ru": return "ru_RU-irina-medium-int8";
            case "ar": return "ar_JO-kareem-medium-int8";
            case "zh": return "zh_CN-huayan-medium";
            default: return "en_US-lessac-medium-int8";
        }
    }

    static File root(Context c) { File d = new File(c.getFilesDir(), "tts"); d.mkdirs(); return d; }
    static File dir(Context c, String lang) { return new File(root(c), lang); }
    static File espeak(Context c) { return new File(root(c), "espeak-ng-data"); }

    static boolean ready(Context c, String lang) {
        File d = dir(c, lang);
        return new File(d, "model.onnx").isFile() && new File(d, "tokens.txt").isFile() && new File(espeak(c), ".ok").isFile();
    }

    static JSONObject status(Context c) throws Exception {
        JSONObject o = new JSONObject();
        for (String l : LANGS) o.put(l, ready(c, l));
        return o;
    }

    static JSONObject download(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        final String lang = a.optString("lang", "en");
        final Context ctx = c;
        String url = GH + voice(lang) + ".tar.bz2";
        File arc = new File(root(c), lang + ".tar.bz2");
        long n = AsrModels.size(url);
        if (!arc.isFile() || arc.length() != n) {
            final AudioTools.Progress pp = p;
            AsrModels.fetch(url, arc, n, 0, n, new AudioTools.Progress() { @Override public void on(double f) { pp.on(f * 0.85); } }, cancel);
        }
        p.on(0.86);
        final boolean haveEs = new File(espeak(c), ".ok").isFile();
        final File d = dir(c, lang);
        d.mkdirs();
        InputStream in = new FileInputStream(arc);
        try {
            Untar.extract(in, new Untar.Place() {
                @Override public File to(String name) {
                    int es = name.indexOf("espeak-ng-data/");
                    if (es >= 0) return haveEs ? null : new File(espeak(ctx), name.substring(es + 15));
                    String base = name.substring(name.lastIndexOf('/') + 1);
                    if (base.endsWith(".onnx")) return new File(d, "model.onnx");
                    if (base.equals("tokens.txt")) return new File(d, "tokens.txt");
                    return null;
                }
            });
        } finally {
            in.close();
        }
        if (!haveEs) new FileOutputStream(new File(espeak(c), ".ok")).close();
        arc.delete();
        if (!ready(c, lang)) throw new IllegalStateException("tts-model");
        return status(c);
    }

    static JSONObject delete(Context c, JSONObject a) throws Exception {
        File[] fs = dir(c, a.optString("lang", "")).listFiles();
        if (fs != null) for (File f : fs) f.delete();
        boolean any = false;
        for (String l : LANGS) any |= ready(c, l);
        if (!any) { File[] es = espeak(c).listFiles(); deleteAll(es); }
        return status(c);
    }

    private static void deleteAll(File[] fs) {
        if (fs == null) return;
        for (File f : fs) { if (f.isDirectory()) deleteAll(f.listFiles()); f.delete(); }
    }

    /** a: {text, lang, engine: phone|ai, speed}. Returns a project clip {path, dur, ch} */
    static JSONObject speak(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        String text = a.getString("text").trim();
        if (text.isEmpty()) throw new IllegalStateException("tts-empty");
        String lang = a.optString("lang", "en");
        float speed = (float) Math.max(0.5, Math.min(2, a.optDouble("speed", 1)));
        return "ai".equals(a.optString("engine")) ? ai(c, text, lang, speed, p, cancel) : phone(c, text, lang, speed, p, cancel);
    }

    private static JSONObject ai(Context c, String text, String lang, float speed, final AudioTools.Progress p, final AtomicBoolean cancel) throws Exception {
        if (!ready(c, lang)) throw new IllegalStateException("tts-model");
        File d = dir(c, lang);
        OfflineTtsConfig cfg = new OfflineTtsConfig();
        cfg.model.vits.model = new File(d, "model.onnx").getAbsolutePath();
        cfg.model.vits.tokens = new File(d, "tokens.txt").getAbsolutePath();
        cfg.model.vits.dataDir = espeak(c).getAbsolutePath();
        cfg.model.numThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        OfflineTts tts = new OfflineTts(cfg);
        GeneratedAudio g;
        final int total = Math.max(1, text.length());
        final int[] made = { 0 };
        try {
            final int rate0 = tts.sampleRate();
            g = tts.generate(text, 0, speed, new OfflineTts.Callback() {
                @Override public Integer invoke(float[] s) {
                    made[0] += s.length;
                    // about 14 characters a second of speech: a rough progress
                    p.on(Math.min(0.9, made[0] / (double) rate0 * 14 / total));
                    return cancel != null && cancel.get() ? 0 : 1;
                }
            });
        } finally {
            tts.release();
        }
        if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
        if (g == null || g.samples == null || g.samples.length == 0) throw new IllegalStateException("tts-fail");
        float[][] y = Resampler.resample(new float[][] { g.samples }, (double) g.sampleRate, (double) Timeline.RATE);
        return clip(c, y);
    }

    static JSONObject clip(Context c, float[][] y) throws Exception {
        float pk = 0;
        for (float v : y[0]) pk = Math.max(pk, Math.abs(v));
        if (pk > 0.98f) for (int i = 0; i < y[0].length; i++) y[0][i] *= 0.98f / pk;
        File out = new File(Timeline.dir(c), "c" + System.nanoTime() + ".wav");
        Studio.WavNode w = new Studio.WavNode(out, Timeline.RATE);
        w.push(y, y[0].length);
        w.end();
        JSONObject o = new JSONObject();
        o.put("path", out.getAbsolutePath());
        o.put("dur", y[0].length * 1000.0 / Timeline.RATE);
        o.put("ch", 1);
        return o;
    }

    /* ---------- the phone's own voice ---------- */

    private static JSONObject phone(Context c, String text, String lang, float speed, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        final CountDownLatch ready = new CountDownLatch(1);
        final int[] st = { TextToSpeech.ERROR };
        final TextToSpeech[] h = new TextToSpeech[1];
        h[0] = new TextToSpeech(c.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) { st[0] = status; ready.countDown(); }
        });
        TextToSpeech tts = h[0];
        File tmpDir = new File(c.getCacheDir(), "tts");
        tmpDir.mkdirs();
        ArrayList<File> parts = new ArrayList<>();
        try {
            if (!ready.await(15, TimeUnit.SECONDS) || st[0] != TextToSpeech.SUCCESS) throw new IllegalStateException("tts-none");
            int lr = tts.setLanguage(Locale.forLanguageTag(lang));
            if (lr == TextToSpeech.LANG_MISSING_DATA || lr == TextToSpeech.LANG_NOT_SUPPORTED) throw new IllegalStateException("tts-lang");
            tts.setSpeechRate(speed);
            final String[] errUtt = { null };
            final CountDownLatch[] done = new CountDownLatch[1];
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { done[0].countDown(); }
                @Override public void onError(String id) { errUtt[0] = id; done[0].countDown(); }
            });
            ArrayList<String> pieces = split(text, Math.min(3000, TextToSpeech.getMaxSpeechInputLength() - 10));
            for (int i = 0; i < pieces.size(); i++) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                File f = new File(tmpDir, "p" + i + ".wav");
                done[0] = new CountDownLatch(1);
                if (tts.synthesizeToFile(pieces.get(i), new Bundle(), f, "u" + i) != TextToSpeech.SUCCESS) throw new IllegalStateException("tts-fail");
                if (!done[0].await(120, TimeUnit.SECONDS) || errUtt[0] != null || !f.isFile() || f.length() < 100) throw new IllegalStateException("tts-fail");
                parts.add(f);
                p.on(0.8 * (i + 1) / pieces.size());
            }
        } finally {
            tts.shutdown();
        }
        // decode every piece to the project format and join them
        ArrayList<File> clips = new ArrayList<>();
        try {
            for (File f : parts) {
                JSONObject src = new JSONObject().put("path", f.getAbsolutePath());
                clips.add(new File(Timeline.add(c, new JSONObject().put("src", src), NOP, cancel).getString("path")));
            }
            if (clips.size() == 1) {
                Workspace.Info i = Workspace.info(clips.get(0));
                JSONObject o = new JSONObject();
                o.put("path", clips.get(0).getAbsolutePath());
                o.put("dur", i.frames * 1000.0 / i.rate);
                o.put("ch", i.ch);
                clips.clear();
                return o;
            }
            Workspace.Info i0 = Workspace.info(clips.get(0));
            File out = new File(Timeline.dir(c), "c" + System.nanoTime() + ".wav");
            long frames = 0;
            OutputStream os = new java.io.BufferedOutputStream(new FileOutputStream(out), 1 << 16);
            try {
                os.write(app.sesdefteri.dsp.Wav.header(i0.ch, i0.rate, false, 0));
                byte[] b = new byte[1 << 16];
                for (File f : clips) {
                    Workspace.Info i = Workspace.info(f);
                    if (i.ch != i0.ch) throw new IllegalStateException("tts-fmt");
                    InputStream is = new FileInputStream(f);
                    try {
                        if (is.skip(44) != 44) throw new IllegalStateException("wav");
                        int n;
                        while ((n = is.read(b)) > 0) os.write(b, 0, n);
                    } finally { is.close(); }
                    frames += i.frames;
                }
            } finally { os.close(); }
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(out, "rw");
            try { raf.write(app.sesdefteri.dsp.Wav.header(i0.ch, i0.rate, false, frames)); } finally { raf.close(); }
            JSONObject o = new JSONObject();
            o.put("path", out.getAbsolutePath());
            o.put("dur", frames * 1000.0 / i0.rate);
            o.put("ch", i0.ch);
            return o;
        } finally {
            for (File f : parts) f.delete();
            for (File f : clips) f.delete();
        }
    }

    static final AudioTools.Progress NOP = new AudioTools.Progress() { @Override public void on(double f) { } };

    /** pieces of at most max characters, cut after sentence ends where possible */
    static ArrayList<String> split(String s, int max) {
        ArrayList<String> out = new ArrayList<>();
        while (s.length() > max) {
            int cut = -1;
            for (int i = max; i > max / 2; i--) { char ch = s.charAt(i - 1); if (ch == '.' || ch == '!' || ch == '?' || ch == '\n' || ch == '。') { cut = i; break; } }
            if (cut < 0) cut = s.lastIndexOf(' ', max);
            if (cut <= 0) cut = max;
            out.add(s.substring(0, cut).trim());
            s = s.substring(cut).trim();
        }
        if (!s.isEmpty()) out.add(s);
        return out;
    }
}
