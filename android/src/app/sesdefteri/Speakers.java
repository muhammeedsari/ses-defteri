package app.sesdefteri;

import android.content.Context;

import app.sesdefteri.dsp.Resampler;

import com.k2fsa.sherpa.onnx.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 3.2: who speaks when (speaker diarization, sherpa-onnx). The pyannote segmentation model (1.5 MB) is in the APK,
 * the voice-print model (CAM++, ≈28 MB) is downloaded once from GitHub on request. Everything runs offline after that.
 */
final class Speakers {
    private Speakers() { }

    static final String EMB = "campplus.onnx";
    static final String URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx";
    static final int RATE = 16000, MAX_SEC = 30 * 60;

    static File emb(Context c) { File d = new File(c.getFilesDir(), "dia"); d.mkdirs(); return new File(d, EMB); }

    static boolean ready(Context c) { return emb(c).isFile(); }

    static JSONObject download(Context c, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File f = emb(c);
        if (!f.isFile()) {
            long n = AsrModels.size(URL);
            AsrModels.fetch(URL, f, n, 0, n, p, cancel);
        }
        return new JSONObject().put("ready", true);
    }

    /** a: {path, in, out (seconds in the file), n: speakers or 0 = find out}. Returns {segs: [[start, end, speaker]] in file seconds, n, db: [level per speaker]} */
    static JSONObject run(Context c, JSONObject a, AudioTools.Progress p, final AtomicBoolean cancel) throws Exception {
        if (!ready(c)) throw new IllegalStateException("dia-model");
        File in = new File(a.getString("path"));
        Workspace.Info inf = Workspace.info(in);
        long from = Math.max(0, (long) (a.optDouble("in", 0) * inf.rate));
        long to = Math.min(inf.frames, a.has("out") ? (long) (a.getDouble("out") * inf.rate) : inf.frames);
        if (to - from > (long) MAX_SEC * inf.rate) throw new IllegalStateException("dia-long");
        if (to - from < inf.rate) throw new IllegalStateException("dia-short");
        // mono 16 kHz, read and resampled in blocks
        final float[] x = new float[(int) ((to - from) * RATE / inf.rate) + 64];
        final int[] len = { 0 };
        Resampler rs = new Resampler(1, (double) inf.rate, (double) RATE);
        rs.to(new app.sesdefteri.dsp.Node() {
            @Override public void push(float[][] b, int n) { int k = Math.min(n, x.length - len[0]); System.arraycopy(b[0], 0, x, len[0], k); len[0] += k; }
            @Override public void end() { }
        });
        RandomAccessFile r = new RandomAccessFile(in, "r");
        try {
            final int BL = 1 << 15;
            float[][] m = new float[1][BL];
            for (long pos = from; pos < to; pos += BL) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                int n = (int) Math.min(BL, to - pos);
                float[][] b = Workspace.readRegion(r, inf, pos, n);
                for (int i = 0; i < n; i++) { float s = 0; for (int ch = 0; ch < inf.ch; ch++) s += b[ch][i]; m[0][i] = s / inf.ch; }
                rs.push(m, n);
            }
            rs.end();
        } finally {
            r.close();
        }
        p.on(0.05);
        float[] mono = java.util.Arrays.copyOf(x, len[0]);

        OfflineSpeakerDiarizationConfig cfg = new OfflineSpeakerDiarizationConfig();
        int th = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        cfg.segmentation.pyannote.model = Separator.model(c, "pyannote.int8.onnx").getAbsolutePath();
        cfg.segmentation.numThreads = th;
        cfg.embedding.model = emb(c).getAbsolutePath();
        cfg.embedding.numThreads = th;
        int want = a.optInt("n", 0);
        cfg.clustering.numClusters = want > 0 ? want : -1;
        cfg.clustering.threshold = 0.5f;
        cfg.minDurationOn = 0.3f;
        cfg.minDurationOff = 0.5f;
        OfflineSpeakerDiarization sd = new OfflineSpeakerDiarization(cfg);
        final AudioTools.Progress pp = p;
        OfflineSpeakerDiarizationSegment[] seg;
        try {
            seg = sd.process(mono, new OfflineSpeakerDiarization.Callback() {
                @Override public Integer invoke(int done, int total, long arg) {
                    if (total > 0) pp.on(0.05 + 0.93 * done / total);
                    return cancel != null && cancel.get() ? 1 : 0;
                }
            });
        } finally {
            sd.release();
        }
        if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
        if (seg == null) throw new IllegalStateException("dia-fail");

        // speakers renumbered by first appearance, level (RMS of their speech) per speaker
        int[] map = new int[64];
        java.util.Arrays.fill(map, -1);
        int n = 0;
        double[] e = new double[64];
        long[] cnt = new long[64];
        double off = from / (double) inf.rate;
        JSONArray out = new JSONArray();
        for (OfflineSpeakerDiarizationSegment s : seg) {
            if (s.speaker < 0 || s.speaker >= 64) continue;
            if (map[s.speaker] < 0) map[s.speaker] = n++;
            int k = map[s.speaker];
            int i0 = Math.max(0, (int) (s.start * RATE)), i1 = Math.min(mono.length, (int) (s.end * RATE));
            for (int i = i0; i < i1; i++) e[k] += mono[i] * mono[i];
            cnt[k] += Math.max(0, i1 - i0);
            out.put(new JSONArray().put(r3(off + s.start)).put(r3(off + s.end)).put(k));
        }
        JSONArray db = new JSONArray();
        for (int k = 0; k < n; k++) db.put(cnt[k] > 0 ? r3(10 * Math.log10(e[k] / cnt[k] + 1e-12)) : -90);
        return new JSONObject().put("segs", out).put("n", n).put("db", db);
    }

    private static double r3(double v) { return Math.round(v * 1000) / 1000.0; }
}
