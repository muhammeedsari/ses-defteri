package app.sesdefteri;

import android.content.Context;

import app.sesdefteri.asr.Keywords;
import app.sesdefteri.asr.Transcriber;
import app.sesdefteri.dsp.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android side of the sound studio: decoding into the pure-Java processing chains, encoding, saving. */
final class Studio {
    private Studio() { }

    static final class Fmt { int ch = 1, rate = 44100; }

    private static Boolean asr;

    /** speech-to-text needs the native library (shipped for 64-bit ARM phones) */
    static synchronized boolean asrAvailable() {
        if (asr == null) {
            try { System.loadLibrary("sherpa-onnx-jni"); asr = true; } catch (Throwable e) { asr = false; }
        }
        return asr;
    }

    private static final class Probed extends RuntimeException { Probed() { super("probed"); } }

    /** channel count and sample rate as the decoder actually delivers them */
    static Fmt probe(Context c, JSONObject src) throws Exception {
        final Fmt f = new Fmt();
        try {
            AudioTools.decode(c, src, new AudioTools.PcmSink() {
                @Override public void pcm(short[] d, int count, int ch, int rate) {
                    f.ch = ch; f.rate = rate;
                    throw new Probed();
                }
            }, null, null);
        } catch (Probed ignored) { }
        f.ch = Math.max(1, Math.min(2, f.ch));
        return f;
    }

    /** a Pipeline.Source that decodes the file on every run; pass k reports progress in its own slice */
    static Pipeline.Source source(final Context c, final JSONObject src, final Fmt f, final AudioTools.Progress prog,
                                  final double[][] slices, final AtomicBoolean cancel) {
        return new Pipeline.Source() {
            int pass = 0;

            @Override public void run(final Node sink) throws Exception {
                final double[] sl = slices != null && pass < slices.length ? slices[pass] : null;
                pass++;
                final float[][] buf = new float[f.ch][4096];
                AudioTools.decode(c, src, new AudioTools.PcmSink() {
                    @Override public void pcm(short[] d, int count, int ch, int rate) throws Exception {
                        int frames = count / ch, p = 0;
                        while (p < frames) {
                            int n = Math.min(4096, frames - p);
                            for (int i = 0; i < n; i++) {
                                int b = (p + i) * ch;
                                for (int k = 0; k < f.ch; k++) buf[k][i] = d[b + Math.min(k, ch - 1)] / 32768f;
                            }
                            sink.push(buf, n);
                            p += n;
                        }
                    }
                }, sl == null || prog == null ? null : new AudioTools.Progress() {
                    @Override public void on(double x) { prog.on(sl[0] + (sl[1] - sl[0]) * x); }
                }, cancel);
                sink.end();
            }
        };
    }

    /** planar float → AAC encoder (16-bit) */
    static final class EncNode extends Node {
        final AudioTools.Encoder enc;
        final int rate, bitrate;
        short[] s = new short[0];
        boolean done;

        EncNode(File out, int rate, int bitrate) { enc = new AudioTools.Encoder(out); this.rate = rate; this.bitrate = bitrate; }

        @Override public void push(float[][] b, int n) throws Exception {
            int ch = b.length;
            if (!enc.started) { enc.bitrate = bitrate; enc.start(rate, ch); }
            if (s.length < n * ch) s = new short[n * ch];
            int q = 0;
            for (int i = 0; i < n; i++) for (int c = 0; c < ch; c++) {
                float v = b[c][i];
                int x = Math.round(v * 32767f);
                s[q++] = (short) (x > 32767 ? 32767 : x < -32768 ? -32768 : x);
            }
            enc.feed(s, q);
        }

        @Override public void end() throws Exception { if (!done) { done = true; enc.finish(); } }

        void abort() { enc.abort(); }
    }

    /** planar float → 16-bit WAV file */
    static final class WavNode extends Node {
        final File f;
        final int rate;
        java.io.OutputStream os;
        Wav.Writer w;
        long frames;
        int ch;

        WavNode(File f, int rate) { this.f = f; this.rate = rate; }

        @Override public void push(float[][] b, int n) throws Exception {
            if (w == null) {
                ch = b.length;
                os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(f), 1 << 16);
                w = new Wav.Writer(os, ch, rate, false, 0);
            }
            w.write(b, n);
            frames += n;
        }

        @Override public void end() throws Exception {
            if (os != null) os.close();
            RandomAccessFile raf = new RandomAccessFile(f, "rw");
            try { Wav.patch(raf, frames, Math.max(1, ch), rate, false); } finally { raf.close(); }
        }
    }

    static double[] doubles(JSONArray a) {
        if (a == null) return null;
        double[] d = new double[a.length()];
        for (int i = 0; i < d.length; i++) d[i] = a.optDouble(i, 0);
        return d;
    }

    static JSONArray json(double[] d) throws Exception {
        JSONArray a = new JSONArray();
        if (d != null) for (double v : d) a.put(Math.round(v * 100) / 100.0);
        return a;
    }

    /** output node for a target: resample down when the platform wants a lower rate, then AAC */
    static Node[] encoderFor(File out, int fs, Enhancer.Target t, boolean voice) {
        int rate = Math.min(fs, t.maxRate);
        EncNode enc = new EncNode(out, rate, voice ? t.bitrateVoice : t.bitrateMusic);
        if (rate == fs) return new Node[] { enc, enc };
        return new Node[] { new ChannelAdaptiveResampler(fs, rate, enc), enc };
    }

    /** resampler that adopts the channel count of the first block */
    static final class ChannelAdaptiveResampler extends Node {
        final int from, to;
        final Node out;
        Resampler r;

        ChannelAdaptiveResampler(int from, int to, Node out) { this.from = from; this.to = to; this.out = out; }

        @Override public void push(float[][] b, int n) throws Exception {
            if (r == null) { r = new Resampler(b.length, from, to); r.to(out); }
            r.push(b, n);
        }

        @Override public void end() throws Exception { if (r != null) r.end(); else out.end(); }
    }

    static File cacheFile(Context c, String sub, String ext) {
        File d = new File(c.getCacheDir(), sub);
        if (!d.exists()) d.mkdirs();
        return new File(d, "sd-" + System.nanoTime() + ext);
    }

    static String safe(String n) {
        String s = n == null ? "" : n.replaceAll("[\\\\/:*?\"<>|]+", " ").replaceAll("\\s+", " ").trim();
        if (s.length() > 80) s = s.substring(0, 80).trim();
        return s.isEmpty() ? "Ses Defteri" : s;
    }

    /* ---------- automatic enhancement ---------- */

    static JSONObject enhance(Context c, JSONObject src, JSONObject a, final AudioTools.Progress prog, AtomicBoolean cancel) throws Exception {
        Fmt f = probe(c, src);
        Enhancer.Options o = new Enhancer.Options();
        o.target = a.optString("target", "youtube");
        o.voice = a.optBoolean("voice", true);
        o.learnedBands = doubles(a.optJSONArray("learned"));
        Enhancer.Target t = Enhancer.Target.of(o.target);
        File temp = cacheFile(c, "work", ".pcm"), out = cacheFile(c, "work", ".m4a");
        Node[] enc = encoderFor(out, f.rate, t, o.voice);
        Pipeline.Result r;
        try {
            r = Pipeline.enhance(source(c, src, f, prog, new double[][] { { 0, 0.25 }, { 0.25, 0.7 } }, cancel), f.ch, f.rate, o, temp, enc[0],
                new Pipeline.Progress() { @Override public void on(double x) { if (x >= 0.7) prog.on(x); } });
        } catch (Exception e) {
            ((EncNode) enc[1]).abort();
            temp.delete();
            throw e;
        }
        try {
            String name = safe(a.optString("name", "Ses")) + ".m4a";
            MediaLib.Target tg = MediaLib.create(c, name, "audio/mp4");
            JSONObject item = MediaLib.copyInTarget(c, tg, out);
            if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
            JSONObject res = new JSONObject();
            res.put("item", item);
            res.put("report", new JSONObject(r.json));
            res.put("tone", json(r.toneAfter));
            res.put("trimStart", (double) r.plan.trimStart / r.plan.fs);
            JSONObject moved = a.optBoolean("keepOriginal", true) ? MediaLib.moveOwn(c, src, true) : null;
            res.put("origSrc", moved != null ? moved : src);
            res.put("origMoved", moved != null);
            if (!o.voice) {
                Beats b = Beats.track(r.plan.analysis.onset, r.plan.analysis.onsetRate);
                res.put("beats", beatsJson(b, r.plan.trimStart / (double) r.plan.fs - r.plan.analysis.fftSize / 2.0 / r.plan.fs));
            }
            return res;
        } finally {
            out.delete();
        }
    }

    static JSONObject beatsJson(Beats b, double shift) throws Exception {
        JSONObject o = new JSONObject();
        o.put("bpm", Math.round(b.bpm * 10) / 10.0);
        o.put("confidence", Math.round(b.confidence * 100) / 100.0);
        JSONArray t = new JSONArray();
        if (b.confidence >= 0.35) for (double x : b.times) { double v = x - shift; if (v >= 0) t.put(Math.round(v * 1000) / 1000.0); }
        o.put("times", t);
        return o;
    }

    /** undo: delete the enhanced copy, put the original back */
    static JSONObject restore(Context c, JSONObject enhancedSrc, JSONObject origSrc, boolean moved) throws Exception {
        JSONObject back = moved ? MediaLib.moveOwn(c, origSrc, false) : origSrc;
        if (back == null) back = origSrc;
        JSONObject item = MediaLib.itemFor(c, back);
        if (item == null) throw new IllegalStateException("original missing");
        boolean del = MediaLib.deleteOwn(c, enhancedSrc);
        JSONObject res = new JSONObject();
        res.put("item", item);
        res.put("deleted", del);
        return res;
    }

    /* ---------- beats only ---------- */

    static JSONObject beats(Context c, JSONObject src, AudioTools.Progress prog, AtomicBoolean cancel) throws Exception {
        Fmt f = probe(c, src);
        Analyzer an = new Analyzer(f.ch, f.rate);
        source(c, src, f, prog, new double[][] { { 0, 1 } }, cancel).run(an);
        Analyzer.Analysis a = an.finish();
        return beatsJson(Beats.track(a.onset, a.onsetRate), -a.fftSize / 2.0 / a.fs);
    }

    /* ---------- speech to text ---------- */

    static JSONObject transcribe(Context c, JSONObject src, String lang, final AudioTools.Progress prog, AtomicBoolean cancel) throws Exception {
        Fmt f = probe(c, src);
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        final Transcriber tr;
        try {
            tr = new Transcriber(c.getAssets(), "asr", lang == null ? "" : lang, threads);
        } catch (Throwable e) {
            throw new IllegalStateException("asr-unavailable: " + e.getMessage());
        }
        try {
            Stages.ChannelMap mono = new Stages.ChannelMap(f.ch, Stages.ChannelMap.MONO);
            Resampler rs = new Resampler(1, f.rate, Transcriber.RATE);
            mono.to(rs).to(new Node() {
                @Override public void push(float[][] b, int n) { tr.accept(b[0], n); }
                @Override public void end() { }
            });
            source(c, src, f, prog, new double[][] { { 0, 0.98 } }, cancel).run(mono);
            List<Transcriber.Segment> segs = tr.finish();
            JSONObject res = new JSONObject();
            JSONArray sa = new JSONArray();
            for (Transcriber.Segment s : segs) {
                JSONArray x = new JSONArray();
                x.put(Math.round(s.start * 100) / 100.0); x.put(Math.round(s.end * 100) / 100.0); x.put(s.text);
                sa.put(x);
            }
            String text = Transcriber.text(segs);
            res.put("segments", sa);
            res.put("text", text);
            res.put("srt", Transcriber.srt(segs));
            JSONArray tags = new JSONArray();
            for (String s : Keywords.tags(text, 5)) tags.put(s);
            res.put("tags", tags);
            res.put("title", Keywords.title(segs));
            res.put("lang", tr.detectedLang);
            return res;
        } finally {
            tr.release();
        }
    }

    /* ---------- speed / pitch ---------- */

    static JSONObject timePitch(Context c, JSONObject src, JSONObject a, AudioTools.Progress prog, AtomicBoolean cancel) throws Exception {
        Fmt f = probe(c, src);
        double speed = Math.max(0.5, Math.min(2.0, a.optDouble("speed", 1)));
        double st = Math.max(-12, Math.min(12, a.optDouble("semitones", 0)));
        boolean formant = a.optBoolean("formant", true);
        File out = cacheFile(c, "work", ".m4a");
        EncNode enc = new EncNode(out, f.rate, f.ch > 1 ? 192000 : 128000);
        TimePitch tp = new TimePitch(f.ch, f.rate, speed, st, formant);
        tp.to(enc);
        try {
            source(c, src, f, prog, new double[][] { { 0, 0.98 } }, cancel).run(tp);
            MediaLib.Target tg = MediaLib.create(c, safe(a.optString("name", "Ses")) + ".m4a", "audio/mp4");
            JSONObject item = MediaLib.copyInTarget(c, tg, out);
            if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
            JSONObject res = new JSONObject();
            res.put("item", item);
            return res;
        } catch (Exception e) {
            enc.abort();
            throw e;
        } finally {
            out.delete();
        }
    }

    /* ---------- background music with ducking ---------- */

    static JSONObject mix(Context c, JSONObject src, JSONObject a, final AudioTools.Progress prog, AtomicBoolean cancel) throws Exception {
        Fmt fv = probe(c, src);
        JSONObject msrc = a.getJSONObject("music");
        Fmt fm = probe(c, msrc);
        File mtemp = cacheFile(c, "work", ".pcm"), temp = cacheFile(c, "work", ".pcm"), out = cacheFile(c, "work", ".m4a");
        Pipeline.FileTemp mt = new Pipeline.FileTemp(mtemp, fm.ch);
        try {
            Node head = mt;
            if (fm.rate != fv.rate) { Resampler r = new Resampler(fm.ch, fm.rate, fv.rate); r.to(mt); head = r; }
            source(c, msrc, fm, prog, new double[][] { { 0, 0.15 } }, cancel).run(head);
            String target = a.optString("target", "youtube");
            Enhancer.Target t = Enhancer.Target.of(target);
            Node[] enc = encoderFor(out, fv.rate, t, false);
            Pipeline.mix(source(c, src, fv, prog, new double[][] { { 0.15, 0.25 }, { 0.25, 0.7 } }, cancel), fv.ch, fv.rate, mt,
                a.optDouble("bedDb", -6), a.optDouble("duckDb", -14), target, temp, enc[0],
                new Pipeline.Progress() { @Override public void on(double x) { if (x >= 0.7) prog.on(x); } });
            MediaLib.Target tg = MediaLib.create(c, safe(a.optString("name", "Ses")) + ".m4a", "audio/mp4");
            JSONObject item = MediaLib.copyInTarget(c, tg, out);
            if (item == null) { MediaLib.discard(c, tg); throw new IllegalStateException("publish"); }
            JSONObject res = new JSONObject();
            res.put("item", item);
            return res;
        } finally {
            mt.delete();
            temp.delete();
            out.delete();
        }
    }

    /* ---------- export for a platform (or lossless WAV) ---------- */

    static JSONObject export(Context c, JSONObject src, JSONObject a, final AudioTools.Progress prog, AtomicBoolean cancel) throws Exception {
        Fmt f = probe(c, src);
        String fmt = a.optString("format", "youtube");
        String name = safe(a.optString("name", "Ses"));
        File dir = new File(c.getCacheDir(), "export");
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) for (File x : old) x.delete();
        File out;
        if ("wav".equals(fmt)) {
            out = new File(dir, name + ".wav");
            WavNode w = new WavNode(out, f.rate);
            source(c, src, f, prog, new double[][] { { 0, 0.98 } }, cancel).run(w);
        } else {
            Enhancer.Options o = new Enhancer.Options();
            o.target = fmt;
            o.voice = a.optBoolean("voice", true);
            o.trim = o.denoise = o.deess = o.eq = o.clicks = o.plosives = o.compress = false;
            String suffix = "whatsapp".equals(fmt) ? " (WhatsApp)" : "podcast".equals(fmt) ? " (Podcast)" : " (YouTube)";
            out = new File(dir, name + suffix + ".m4a");
            File temp = cacheFile(c, "work", ".pcm");
            Node[] enc = encoderFor(out, f.rate, Enhancer.Target.of(fmt), o.voice);
            Pipeline.enhance(source(c, src, f, prog, new double[][] { { 0, 0.3 }, { 0.3, 0.7 } }, cancel), f.ch, f.rate, o, temp, enc[0],
                new Pipeline.Progress() { @Override public void on(double x) { if (x >= 0.7) prog.on(x); } });
        }
        JSONObject res = new JSONObject();
        JSONObject s = new JSONObject();
        s.put("path", out.getAbsolutePath());
        res.put("src", s);
        res.put("mime", "wav".equals(fmt) ? "audio/wav" : "audio/mp4");
        res.put("name", out.getName());
        return res;
    }
}
