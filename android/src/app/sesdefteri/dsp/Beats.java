package app.sesdefteri.dsp;

/**
 * Tempo and beat positions from an onset-strength curve (spectral flux, see {@link Analyzer}):
 * tempo by autocorrelation with a mild preference around 120 BPM, beats by dynamic programming (Ellis 2007).
 */
public final class Beats {
    public double bpm;
    public double[] times;          // seconds
    public double confidence;       // 0..1, how periodic the onsets are

    public static Beats track(float[] onset, double rate) {
        Beats b = new Beats();
        int n = onset.length;
        if (n < rate * 4) { b.times = new double[0]; return b; }
        // normalise: remove slow trend, keep positive part, unit std
        double[] o = new double[n];
        int W = (int) Math.round(rate * 0.5);
        double run = 0;
        double[] pre = new double[n + 1];
        for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + onset[i];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - W), z = Math.min(n, i + W + 1);
            double m = (pre[z] - pre[a]) / (z - a);
            o[i] = Math.max(0, onset[i] - m);
        }
        double s2 = 0;
        for (double v : o) s2 += v * v;
        double sd = Math.sqrt(s2 / n) + 1e-9;
        for (int i = 0; i < n; i++) o[i] /= sd;
        // tempo
        int lo = (int) Math.floor(rate * 60 / 200), hi = (int) Math.ceil(rate * 60 / 55);
        double[] ac = new double[hi + 2];
        for (int lag = lo - 1; lag <= hi + 1; lag++) {
            double s = 0;
            for (int i = lag; i < n; i++) s += o[i] * o[i - lag];
            ac[lag] = s / (n - lag);
        }
        double best = -1; int bl = lo;
        for (int lag = lo; lag <= hi; lag++) {
            double bpm = 60 * rate / lag;
            double w = Math.exp(-0.5 * Math.pow(Math.log(bpm / 110) / Math.log(2) / 0.6, 2));
            double v = ac[lag] * w;
            if (v > best) { best = v; bl = lag; }
        }
        double y0 = ac[bl - 1], y1 = ac[bl], y2 = ac[bl + 1];
        double off = (y0 - 2 * y1 + y2) != 0 ? 0.5 * (y0 - y2) / (y0 - 2 * y1 + y2) : 0;
        double period = bl + Math.max(-0.5, Math.min(0.5, off));
        b.bpm = 60 * rate / period;
        b.confidence = Math.max(0, Math.min(1, ac[bl] / (ac0(o) + 1e-9)));
        // dynamic programming
        double alpha = 100;
        double[] C = new double[n];
        int[] back = new int[n];
        for (int t = 0; t < n; t++) {
            int a = (int) Math.round(t - 2 * period), z = (int) Math.round(t - period / 2);
            double bestS = 0; int bt = -1;
            for (int tau = Math.max(0, a); tau <= z && tau < t; tau++) {
                double d = Math.log((t - tau) / period);
                double sc = C[tau] - alpha * d * d;
                if (bt < 0 || sc > bestS) { bestS = sc; bt = tau; }
            }
            C[t] = o[t] + (bt >= 0 ? Math.max(0, bestS) : 0);
            back[t] = bt >= 0 && bestS > 0 ? bt : -1;
        }
        int end = n - 1;
        double mx = -1;
        for (int t = Math.max(0, n - 1 - (int) Math.round(period)); t < n; t++) if (C[t] > mx) { mx = C[t]; end = t; }
        java.util.ArrayList<Double> beats = new java.util.ArrayList<>();
        for (int t = end; t >= 0; t = back[t]) { beats.add(0, t / rate); if (back[t] < 0) break; }
        b.times = new double[beats.size()];
        for (int i = 0; i < b.times.length; i++) b.times[i] = beats.get(i);
        return b;
    }

    private static double ac0(double[] o) { double s = 0; for (double v : o) s += v * v; return s / o.length; }
}
