package app.sesdefteri.dsp;

/**
 * Look-ahead true-peak limiter with optional static gain in front.
 * The gain curve is the sliding minimum of the required gain (ceiling / true peak) over the look-ahead,
 * smoothed by an equal-length moving average, so it is always below what each sample needs
 * and never jumps. Output timeline is aligned with the input (latency removed).
 */
public final class Limiter extends Node {
    private final int ch, L, R, delay;
    private final double ceiling, relCoef;
    private double preGain;
    private final float[][] ring;          // input history (after pre-gain)
    private final double[] tp;             // per-sample true peak, ring
    private final double[] greq;           // required gain, ring
    private final int[] dq; private int dqHead, dqTail;    // monotonic deque of indices (sliding min)
    private final double[] envRing; private double envSum, env = 1;
    private long n;                        // samples received
    private long emitted, total = -1;
    private float[][] out = new float[1][0];
    private int outN;
    public double minGain = 1;             // deepest gain reduction (for the report)
    public long limitedSamples;

    public Limiter(int channels, double fs, double ceilingDb, double preGainDb) {
        ch = channels;
        L = Math.max(16, (int) Math.round(fs * 0.003));
        delay = L + TruePeak.D;
        R = Integer.highestOneBit(delay + TruePeak.T + 8) * 2;
        ceiling = Math.pow(10, (ceilingDb - 0.1) / 20);
        preGain = Math.pow(10, preGainDb / 20);
        relCoef = 1 - Math.exp(-1.0 / (0.12 * fs));
        ring = new float[ch][R];
        tp = new double[R];
        greq = new double[R];
        dq = new int[R];
        envRing = new double[L];
        for (int i = 0; i < L; i++) envRing[i] = 1;
        envSum = L;
    }

    public void setPreGainDb(double db) { preGain = Math.pow(10, db / 20); }

    private float[] tmpWin = new float[TruePeak.T];

    @Override public void push(float[][] b, int cnt) throws Exception {
        ensureOut(cnt);
        for (int i = 0; i < cnt; i++) step(b, i, false);
        flushOut();
    }

    private void ensureOut(int cnt) { if (out.length != ch || out[0].length < cnt + 8) out = new float[ch][cnt + 8]; }

    private void flushOut() throws Exception {
        if (outN > 0 && next != null) next.push(out, outN);
        outN = 0;
    }

    private void step(float[][] b, int i, boolean zero) {
        int w = (int) (n & (R - 1));
        for (int c = 0; c < ch; c++) ring[c][w] = zero ? 0f : (float) (b[c][i] * preGain);
        tp[w] = 0;
        // samples m = n - D get their true peak finalised now
        long m = n - TruePeak.D;
        if (m >= 0) {
            int mi = (int) (m & (R - 1));
            double peak = tp[mi];
            for (int c = 0; c < ch; c++) {
                float[] r = ring[c];
                double a = Math.abs(r[mi]);
                if (a > peak) peak = a;
                for (int j = 1; j <= 3; j++) {
                    double acc = 0;
                    double[] h = TruePeak.TAPS[j - 1];
                    for (int k = 0; k < TruePeak.T; k++) {
                        long idx = m + k - (TruePeak.T / 2 - 1);
                        if (idx >= 0) acc += r[(int) (idx & (R - 1))] * h[k];
                    }
                    double v = Math.abs(acc);
                    if (v > peak) peak = v;
                    int m1 = (int) ((m + 1) & (R - 1));
                    if (v > tp[m1]) tp[m1] = v;      // also bounds the next sample
                }
            }
            tp[mi] = peak;
            double g = peak > ceiling ? ceiling / peak : 1.0;
            greq[mi] = g;
            // sliding minimum over [k, k+L]: push m
            while (dqTail > dqHead && greq[dq[(dqTail - 1) & (R - 1)]] >= g) dqTail--;
            dq[dqTail & (R - 1)] = mi; dqTail++;
            long k = m - L;
            if (k >= 0) {
                // deque front must be within [k, m]
                while (true) {
                    int f = dq[dqHead & (R - 1)];
                    long fAbs = m - ((mi - f) & (R - 1));
                    if (fAbs < k) dqHead++; else break;
                }
                double t = greq[dq[dqHead & (R - 1)]];
                env = t < env ? t : env + (t - env) * relCoef;
                int ki = (int) (k & (R - 1)), ei = (int) (k % L);
                envSum += env - envRing[ei];
                envRing[ei] = env;
                double gain = envSum / L;
                if (gain > 1) gain = 1;
                if (gain < minGain) minGain = gain;
                if (gain < 0.999) limitedSamples++;
                if (total < 0 || emitted < total) {
                    for (int c = 0; c < ch; c++) out[c][outN] = (float) (ring[c][ki] * gain);
                    outN++;
                    emitted++;
                }
            }
        }
        n++;
    }

    @Override public void end() throws Exception {
        total = n;
        int pad = delay + 1;
        float[][] z = new float[ch][1];
        ensureOut(pad);
        for (int i = 0; i < pad; i++) step(z, 0, true);
        flushOut();
        if (next != null) next.end();
    }
}
