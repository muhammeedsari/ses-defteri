package app.sesdefteri;

import org.json.JSONArray;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Frame-level scan of a WAV (20 ms frames) to find long pauses and breaths.
 * Level L (dB RMS) and zero crossings per second Z: breaths are noise (Z ≳ 1800/s), quieter than speech and
 * louder than the room; voiced speech crosses zero far less often. Pauses are runs near the noise floor.
 */
final class Speech {
    private Speech() { }

    static final double FR = 0.02;

    static final class Scan {
        int rate, hop, n;
        float[] lvl, hf;
        double noise, speech;
    }

    static Scan scan(File f, Workspace.Info inf, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        Scan s = new Scan();
        s.rate = inf.rate; s.hop = (int) Math.round(inf.rate * FR);
        s.n = (int) (inf.frames / s.hop);
        s.lvl = new float[s.n]; s.hf = new float[s.n];
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            r.seek(44);
            int ch = inf.ch;
            byte[] b = new byte[s.hop * ch * 2];
            float prev = 0, dc = 0;
            for (int k = 0; k < s.n; k++) {
                if (cancel != null && cancel.get()) throw new InterruptedException("cancel");
                r.readFully(b);
                double e = 0;
                int z = 0;
                for (int i = 0, q = 0; i < s.hop; i++) {
                    float v = 0;
                    for (int c = 0; c < ch; c++, q += 2) v += (short) ((b[q] & 0xff) | (b[q + 1] << 8)) / 32768f;
                    v /= ch;
                    dc += (v - dc) * 0.002f;          // remove DC so crossings count the signal, not an offset
                    float x = v - dc;
                    e += x * x;
                    if ((x >= 0) != (prev >= 0)) z++;
                    prev = x;
                }
                s.lvl[k] = (float) (10 * Math.log10(e / s.hop + 1e-12));
                s.hf[k] = (float) (z / FR);
                if (p != null && k % 500 == 0) p.on((double) k / s.n);
            }
        } finally {
            r.close();
        }
        float[] sorted = s.lvl.clone();
        Arrays.sort(sorted);
        s.noise = s.n > 0 ? sorted[(int) (s.n * 0.1)] : -90;
        s.speech = s.n > 0 ? sorted[(int) (s.n * 0.95)] : -20;
        return s;
    }

    /** silent runs longer than minSec, as [startFrame, endFrame) */
    static ArrayList<int[]> pauses(Scan s, double minSec) {
        double thr = Math.max(s.noise + 6, s.speech - 40);
        ArrayList<int[]> out = new ArrayList<>();
        int start = -1, min = (int) Math.round(minSec / FR);
        for (int k = 0; k <= s.n; k++) {
            boolean q = k < s.n && s.lvl[k] < thr;
            if (q && start < 0) start = k;
            if (!q && start >= 0) { if (k - start >= min && start > 0 && k < s.n) out.add(new int[] { start, k }); start = -1; }
        }
        return out;
    }

    /** breath-like runs (0.15–0.9 s) */
    static ArrayList<int[]> breaths(Scan s) {
        double lo = Math.max(s.noise + 6, s.speech - 45), hi = s.speech - 12;
        ArrayList<int[]> out = new ArrayList<>();
        int start = -1, gap = 0;
        for (int k = 0; k <= s.n; k++) {
            boolean b = k < s.n && s.lvl[k] >= lo && s.lvl[k] <= hi && s.hf[k] > 1800;
            if (b) { if (start < 0) start = k; gap = 0; continue; }
            if (start >= 0 && gap < 2 && k < s.n && s.lvl[k] < s.speech - 8 && s.hf[k] > 1200) { gap++; continue; }   // allow a couple of odd frames inside
            if (start >= 0) {
                int end = k - gap, len = end - start;
                if (len >= 8 && len <= 45 && breathLike(s, start, end)) out.add(new int[] { start, end });
                start = -1; gap = 0;
            }
        }
        return out;
    }

    /** a breath sits next to a pause and is clearly quieter than speech; "s"/"ş" inside words don't */
    private static boolean breathLike(Scan s, int a, int b) {
        double sum = 0, zc = 0;
        for (int k = a; k < b; k++) { sum += Math.pow(10, s.lvl[k] / 10); zc += s.hf[k]; }
        double mean = 10 * Math.log10(sum / (b - a));
        if (mean > s.speech - 16) return false;
        if (zc / (b - a) > 9000) return false;            // "s"/"ş" hiss is much higher than a breath
        double quiet = Math.max(s.noise + 8, s.speech - 38);
        return quietRun(s, Math.max(0, a - 15), a, quiet) || quietRun(s, b, Math.min(s.n, b + 15), quiet);
    }

    /** at least 3 quiet frames in a row (a real pause, not a dip between sounds) */
    private static boolean quietRun(Scan s, int a, int b, double quiet) {
        int run = 0;
        for (int k = a; k < b; k++) { run = s.lvl[k] < quiet ? run + 1 : 0; if (run >= 3) return true; }
        return false;
    }

    /**
     * Hesitation sounds ("ııı", "eee", "mmm"): a voiced sound held for ≥ 0.35 s with a steady pitch and level.
     * Ordinary syllables are much shorter and their pitch keeps moving. Works on 16 kHz mono.
     */
    static ArrayList<double[]> fillers(float[] x, int rate) {
        final int win = rate * 3 / 100, hop = rate / 100, minLag = rate / 400, maxLag = rate / 75;
        int n = Math.max(0, (x.length - win) / hop);
        float[] f0 = new float[n], lv = new float[n];
        boolean[] voiced = new boolean[n];
        float[] sorted = new float[n];
        for (int k = 0; k < n; k++) {
            int o = k * hop;
            double e = 0;
            for (int i = 0; i < win; i++) e += x[o + i] * x[o + i];
            lv[k] = (float) (10 * Math.log10(e / win + 1e-12));
            sorted[k] = lv[k];
            if (e < 1e-7 * win) continue;
            double best = 0; int bl = 0;
            for (int lag = minLag; lag <= maxLag; lag++) {
                double r = 0, e1 = 0, e2 = 0;
                for (int i = 0; i + lag < win; i += 2) { double a = x[o + i], b = x[o + i + lag]; r += a * b; e1 += a * a; e2 += b * b; }
                double c = r / Math.sqrt(e1 * e2 + 1e-12);
                if (c > best) { best = c; bl = lag; }
            }
            if (best > 0.75) { voiced[k] = true; f0[k] = (float) rate / bl; }
        }
        Arrays.sort(sorted);
        double speech = n > 0 ? sorted[(int) (n * 0.95)] : -20;
        ArrayList<double[]> out = new ArrayList<>();
        int k = 0, minRun = 38;
        while (k < n) {
            if (!voiced[k] || lv[k] < speech - 30) { k++; continue; }
            int a = k;
            // grow while pitch stays within ±8 % of the running median and the level within 8 dB
            double ref = f0[k], refL = lv[k];
            int miss = 0, e = k;
            for (int j = k + 1; j < n; j++) {
                boolean ok = voiced[j] && Math.abs(f0[j] - ref) / ref < 0.08 && Math.abs(lv[j] - refL) < 8;
                if (ok) { e = j; miss = 0; ref = 0.9 * ref + 0.1 * f0[j]; refL = 0.9 * refL + 0.1 * lv[j]; }
                else if (++miss > 3) break;
            }
            if (e - a + 1 >= minRun) {
                // steadiness check over the whole run: real words glide, hesitations sit still
                double m = 0, v = 0; int cnt = 0;
                for (int j = a; j <= e; j++) if (voiced[j]) { m += f0[j]; cnt++; }
                m /= Math.max(1, cnt);
                for (int j = a; j <= e; j++) if (voiced[j]) v += (f0[j] - m) * (f0[j] - m);
                double cv = Math.sqrt(v / Math.max(1, cnt)) / m;
                if (cv < 0.035) out.add(new double[] { Math.max(0, a * hop / (double) rate - 0.04), Math.min(x.length / (double) rate, (e * hop + win) / (double) rate + 0.04) });
            }
            k = e + 1;
        }
        return out;
    }

    static JSONArray json(ArrayList<int[]> l) throws Exception {
        JSONArray a = new JSONArray();
        for (int[] r : l) { JSONArray x = new JSONArray(); x.put(r[0] * FR); x.put(r[1] * FR); a.put(x); }
        return a;
    }
}
