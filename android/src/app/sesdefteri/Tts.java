package app.sesdefteri;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

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
 * 3.4 text to speech with the phone's own voice (Android TextToSpeech, nothing to download). The result is a project clip.
 * 5.12: the Piper voices were removed for licence reasons (espeak-ng is GPL-3.0).
 */
final class Tts {
    private Tts() { }

    /** 5.12: the downloadable AI voices are gone (their pronunciation library is GPL); what they left on the phone goes too */
    static void cleanup(Context c) {
        // 5.14.2: also the speech pieces a stopped job left in the cache (where speak() writes them)
        final File d = new File(c.getFilesDir(), "tts"), t = new File(c.getCacheDir(), "tts");
        if (d.isDirectory() || t.isDirectory()) new Thread(new Runnable() { @Override public void run() { deleteTree(d); deleteTree(t); } }).start();
    }
    private static void deleteTree(File f) {
        File[] fs = f.listFiles();
        if (fs != null) for (File x : fs) deleteTree(x);
        f.delete();
    }

    /** a: {text, lang, speed}. Returns a project clip {path, dur, ch} */
    static JSONObject speak(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        String text = a.getString("text").trim();
        if (text.isEmpty()) throw new IllegalStateException("tts-empty");
        String lang = a.optString("lang", "en");
        float speed = (float) Math.max(0.5, Math.min(2, a.optDouble("speed", 1)));
        return phone(c, text, lang, speed, p, cancel);
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
