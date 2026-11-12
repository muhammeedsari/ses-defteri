package app.sesdefteri;

import android.content.Context;

import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult;
import com.k2fsa.sherpa.onnx.OnlineStream;

import app.sesdefteri.asr.Keywords;
import app.sesdefteri.asr.Transcriber;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 5.16: live text while recording, with a seda Turkish streaming model (v1 = seda-v0.1 Apache-2.0, v2 = seda-v0.2 CC BY-SA 4.0,
 * by atasoglu). The recorder thread only copies 20 ms of mono audio into a queue; recognition runs on its own low-priority
 * thread. If the phone falls behind by more than 30 s, audio is skipped for the text (never for the recording).
 */
final class LiveText {
    static final String[] FILES = { "encoder.int8.onnx", "decoder.onnx", "joiner.int8.onnx", "tokens.txt" };
    static final String GH = "https://github.com/muhammeedsari/ses-defteri/releases/download/seda-models/";
    static final String[][] SHA = {
        { "dc28b7ae3f5e67c0daa2d51886dae2ac8287a25333f46a363f3684b3cd1220c9", "2596861911cec5ca1de31adc12a7fc983d05395434f92239f4e007eeae3a5dcc",
          "b4d1cff7c410d14c85c143885954be09a6a4a076cd35d22aeba58655665582bb", "f6e134fa357558d0c4ab56fbe380323c30e3d50a13fe402a68328d5a13c2236b" },
        { "d108635fa0d598ddfc62dfdbb052ce88b7a6622f1d03e09b02554a26d6872e91", "cae04ec40a7505a30c1cf1388b473023e8c8cc31201a34b6fee483383d675809",
          "ed933990d058f3d1b8e2263459e87d4153e6eb74b8d4a55f26074065e67a6717", "f6e134fa357558d0c4ab56fbe380323c30e3d50a13fe402a68328d5a13c2236b" } };

    static String name(String m) { return "v1".equals(m) ? "v1" : "v2"; }
    static String repo(String m) { return "v1".equals(m) ? "seda-v0.1" : "seda-v0.2"; }
    static File dir(Context c, String m) { return new File(c.getFilesDir(), "live-" + name(m)); }

    static {
        for (int k = 0; k < 2; k++) {
            String r = k == 0 ? "seda-v0.1" : "seda-v0.2";
            for (int i = 0; i < FILES.length; i++) {
                AsrModels.SHA.put(GH + r + "-" + FILES[i], SHA[k][i]);
                AsrModels.SHA.put("https://huggingface.co/atasoglu/" + r + "/resolve/main/" + FILES[i], SHA[k][i]);
            }
        }
    }

    static boolean ready(Context c, String m) {
        File d = dir(c, m);
        for (String f : FILES) if (!new File(d, f).isFile()) return false;
        return true;
    }

    static JSONObject status(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("v1", ready(c, "v1"));
        o.put("v2", ready(c, "v2"));
        return o;
    }

    /** GitHub first (the app's own mirror), Hugging Face if that fails */
    static JSONObject download(Context c, JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        String m = name(a.optString("model", "v2")), r = repo(m);
        File d = dir(c, m);
        d.mkdirs();
        String[] hosts = { GH + r + "-", "https://huggingface.co/atasoglu/" + r + "/resolve/main/" };
        Exception last = null;
        for (String h : hosts) {
            try {
                long[] sz = new long[FILES.length];
                long total = 0, done = 0;
                for (int i = 0; i < FILES.length; i++) { sz[i] = AsrModels.size(h + FILES[i]); total += sz[i]; }
                for (int i = 0; i < FILES.length; i++) {
                    File f = new File(d, FILES[i]);
                    if (!(f.isFile() && f.length() == sz[i])) AsrModels.fetch(h + FILES[i], f, sz[i], done, total, p, cancel);
                    done += sz[i];
                }
                return status(c);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                if (cancel != null && cancel.get()) throw e;
                last = e;
            }
        }
        MainActivity.saveNote(c, "live " + m + " download: " + (last == null ? "" : last.getMessage()));
        throw new IllegalStateException("download-unreachable");
    }

    static JSONObject delete(Context c, JSONObject a) throws Exception {
        File[] fs = dir(c, a.optString("model", "v2")).listFiles();
        if (fs != null) for (File f : fs) f.delete();
        return status(c);
    }

    /* ---------- the running engine ---------- */

    private static final class Chunk {
        final float[] x;
        final long at;
        Chunk(float[] x, long at) { this.x = x; this.at = at; }
    }

    private final ArrayBlockingQueue<Chunk> q = new ArrayBlockingQueue<Chunk>(4096);
    private final int rate;
    private final long maxLag;
    private long fed;             // input frames handed over, skipped ones included (recorder thread only)
    private final java.util.concurrent.atomic.AtomicLong queued = new java.util.concurrent.atomic.AtomicLong(); // frames waiting
    private final List<Transcriber.Segment> segs = new ArrayList<Transcriber.Segment>();
    private volatile String part = "";
    private volatile boolean stop, failed;
    private final Thread th;

    LiveText(final Context c, final String model, int rate) {
        this.rate = rate;
        maxLag = rate * 30L;
        final File d = dir(c, model);
        th = new Thread(new Runnable() { @Override public void run() { work(c, d); } }, "livetext");
        th.start();
    }

    /** recorder thread: never blocks, never throws */
    void feed(float[] l, float[] r, int n, int ch) {
        if (stop || failed) return;
        long at = fed;
        fed += n;
        if (queued.get() > maxLag) return;   // too far behind: this piece is left out of the text only
        float[] x = new float[n];
        if (ch > 1) for (int i = 0; i < n; i++) x[i] = (l[i] + r[i]) * 0.5f;
        else System.arraycopy(l, 0, x, 0, n);
        if (q.offer(new Chunk(x, at))) queued.addAndGet(n);
    }

    private void work(Context c, File d) {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        OnlineRecognizer rec = null;
        OnlineStream st = null;
        try {
            OnlineRecognizerConfig cfg = new OnlineRecognizerConfig();
            String p = d.getPath() + "/";
            cfg.modelConfig.transducer.encoder = p + FILES[0];
            cfg.modelConfig.transducer.decoder = p + FILES[1];
            cfg.modelConfig.transducer.joiner = p + FILES[2];
            cfg.modelConfig.tokens = p + FILES[3];
            cfg.modelConfig.numThreads = 1;
            cfg.enableEndpoint = true;
            // measured on the Turkish test set: a 0.5 s pause ends a sentence, no piece longer than 15 s
            cfg.endpointConfig.rule2.minTrailingSilence = 0.5f;
            cfg.endpointConfig.rule3.minUtteranceLength = 15f;
            rec = new OnlineRecognizer(cfg);
            st = rec.createStream();
            long seg0 = 0, pos = 0;
            while (true) {
                Chunk k = q.poll(200, TimeUnit.MILLISECONDS);
                if (k == null) { if (stop) break; continue; }
                queued.addAndGet(-k.x.length);
                if (k.at > pos) {   // audio was skipped: close the sentence so times stay right
                    seg0 = close(rec, st, seg0, pos);
                    seg0 = k.at;
                }
                st.acceptWaveform(k.x, rate);
                pos = k.at + k.x.length;
                while (rec.isReady(st)) rec.decode(st);
                if (rec.isEndpoint(st)) { close(rec, st, seg0, pos); seg0 = pos; }
                else part = rec.getResult(st).text.trim();
            }
            st.acceptWaveform(new float[rate / 2], rate);   // flush the last words
            st.inputFinished();
            while (rec.isReady(st)) rec.decode(st);
            close(rec, st, seg0, pos + rate / 2);
        } catch (Throwable e) {
            failed = true;
            MainActivity.saveNote(c, "live text: " + e);
        } finally {
            if (st != null) st.release();
            if (rec != null) rec.release();
            q.clear();
        }
    }

    /** the sentence so far becomes a segment; returns where the next one starts */
    private long close(OnlineRecognizer rec, OnlineStream st, long seg0, long pos) {
        OnlineRecognizerResult r = rec.getResult(st);
        String t = r.text.trim();
        if (!t.isEmpty()) {
            double s = seg0 / (double) rate + (r.timestamps != null && r.timestamps.length > 0 ? Math.max(0, r.timestamps[0] - 0.2) : 0);
            synchronized (segs) { segs.add(new Transcriber.Segment(s, Math.max(s + 0.3, pos / (double) rate), t)); }
        }
        rec.reset(st);
        part = "";
        return pos;
    }

    /** for the recording screen: finished sentences from index `from`, and the one being spoken */
    String poll(int from) {
        try {
            JSONObject o = new JSONObject();
            JSONArray a = new JSONArray();
            int n;
            synchronized (segs) {
                n = segs.size();
                for (int i = Math.max(0, from); i < n; i++) a.put(segs.get(i).text);
            }
            o.put("n", n);
            o.put("segs", a);
            o.put("part", part);
            o.put("lag", Math.round(queued.get() * 10.0 / rate) / 10.0);
            if (failed) o.put("failed", true);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** ends the engine; waits up to `ms` for what is left, then returns the text as the transcription result */
    JSONObject end(long ms) {
        stop = true;
        if (ms <= 0) return null;
        try { th.join(ms); } catch (InterruptedException ignored) { }
        List<Transcriber.Segment> l;
        synchronized (segs) { l = new ArrayList<Transcriber.Segment>(segs); }
        try {
            JSONObject res = new JSONObject();
            JSONArray sa = new JSONArray();
            for (Transcriber.Segment s : l) {
                JSONArray x = new JSONArray();
                x.put(Math.round(s.start * 100) / 100.0); x.put(Math.round(s.end * 100) / 100.0); x.put(s.text);
                sa.put(x);
            }
            String text = Transcriber.text(l);
            res.put("segments", sa);
            res.put("text", text);
            res.put("srt", Transcriber.srt(l));
            JSONArray tags = new JSONArray();
            for (String s : Keywords.tags(text, 5)) tags.put(s);
            res.put("tags", tags);
            res.put("title", Keywords.title(l));
            res.put("lang", "tr");
            res.put("live", true);
            if (th.isAlive()) res.put("short", true);   // the phone was still behind: the end of the text is missing
            return res;
        } catch (Exception e) {
            return null;
        }
    }
}
