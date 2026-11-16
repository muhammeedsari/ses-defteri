package app.sesdefteri;

import app.sesdefteri.dsp.Fft;

import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 5.21: two people record the same talk on their own phones; this finds how far one recording must move so the two line up.
 * Both sounds become an "onset" curve (how fast the loudness rises, 1 ms steps, the same for two different microphones),
 * the curves are cross-correlated with an FFT. A clap at the start gives the sharpest answer, but plain speech works too.
 */
final class Align {
    private Align() { }

    static final int R = 1000;   // curve points per second

    /** a: {a: {path, in, len}, b: {path, in, len}, max: seconds}; result {lag: seconds b starts after a's start, conf} */
    static JSONObject run(JSONObject a, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        double max = Math.min(600, a.optDouble("max", 60)), win = 180;
        float[] ea = curve(a.getJSONObject("a"), win + max, cancel);
        p.on(0.35);
        float[] eb = curve(a.getJSONObject("b"), win, cancel);
        p.on(0.7);
        if (ea.length < R * 2 || eb.length < R * 2) throw new IllegalStateException("align-short");
        int n = Fft.nextPow2(ea.length + eb.length);
        double[] ar = new double[n], ai = new double[n], br = new double[n], bi = new double[n];
        for (int i = 0; i < ea.length; i++) ar[i] = ea[i];
        for (int i = 0; i < eb.length; i++) br[i] = eb[i];
        Fft f = new Fft(n);
        f.transform(ar, ai, false);
        f.transform(br, bi, false);
        for (int i = 0; i < n; i++) {   // A · conj(B)
            double re = ar[i] * br[i] + ai[i] * bi[i], im = ai[i] * br[i] - ar[i] * bi[i];
            ar[i] = re; ai[i] = im;
        }
        f.transform(ar, ai, true);
        int lim = (int) (max * R);
        double best = -Double.MAX_VALUE, sum = 0, sq = 0; int at = 0, cnt = 0;
        for (int L = -Math.min(lim, eb.length - R); L <= Math.min(lim, ea.length - R); L++) {
            double v = ar[L >= 0 ? L : n + L];
            sum += v; sq += v * v; cnt++;
            if (v > best) { best = v; at = L; }
        }
        double mean = sum / Math.max(1, cnt), sd = Math.sqrt(Math.max(1e-30, sq / Math.max(1, cnt) - mean * mean));
        p.on(1);
        return new JSONObject().put("lag", at / (double) R).put("conf", Math.round((best - mean) / sd * 10) / 10.0);
    }

    /** onset curve of a clip's sound, from its in point, at most sec seconds */
    private static float[] curve(JSONObject s, double sec, AtomicBoolean cancel) throws Exception {
        File in = new File(s.getString("path"));
        Workspace.Info inf = Workspace.info(in);
        long from = Math.max(0, (long) (s.optDouble("in", 0) * inf.rate));
        long to = Math.min(inf.frames, from + (long) (Math.min(sec, s.optDouble("len", sec)) * inf.rate));
        int per = Math.max(1, inf.rate / R), n = (int) Math.max(0, (to - from) / per);
        float[] e = new float[n];
        RandomAccessFile r = new RandomAccessFile(in, "r");
        try {
            final int BL = per * 256;
            float last = 0;
            int k = 0;
            for (long pos = from; pos < to && k < n; pos += BL) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                int m = (int) Math.min(BL, to - pos);
                float[][] b = Workspace.readRegion(r, inf, pos, m);
                for (int i = 0; i + per <= m && k < n; i += per) {
                    double acc = 0;
                    for (int j = i; j < i + per; j++) {
                        float x = 0; for (int ch = 0; ch < inf.ch; ch++) x += b[ch][j];
                        float d = x - last; last = x;   // first difference: low rumble and hum count less
                        acc += Math.abs(d);
                    }
                    e[k++] = (float) Math.log(1e-5 + acc / per);
                }
            }
        } finally {
            r.close();
        }
        // rise of the level, smoothed a little, mean removed
        float[] o = new float[n];
        double mean = 0;
        for (int i = 1; i < n; i++) { float d = e[i] - e[i - 1]; o[i] = d > 0 ? d : 0; mean += o[i]; }
        mean /= Math.max(1, n);
        for (int i = 0; i < n; i++) o[i] -= mean;
        return o;
    }
}
