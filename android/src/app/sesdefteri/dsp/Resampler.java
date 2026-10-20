package app.sesdefteri.dsp;

/**
 * Streaming band-limited resampler (windowed-sinc, Kaiser window), any ratio.
 * Used for Whisper (16 kHz), background music at a different rate, and pitch shifting.
 */
public final class Resampler extends Node {
    private final int ch;
    private final double step;          // input samples per output sample
    private final int half;             // taps on each side
    private final double cutoff;        // relative to input Nyquist
    private static final int PHASES = 512;
    private final float[] table;        // (PHASES+1) * (2*half)
    private float[][] hist;             // input history
    private int histLen;
    private double pos;                 // position of next output in history coordinates
    private float[][] out = new float[1][0];
    private final int outCh;

    public Resampler(int channels, double inRate, double outRate) { this(channels, inRate / outRate, 32); }

    public Resampler(int channels, double step, int half) {
        ch = channels; outCh = channels;
        this.step = step;
        this.half = half;
        cutoff = Math.min(1.0, 1.0 / step) * 0.95;
        double beta = 8.6;
        table = new float[(PHASES + 1) * 2 * half];
        double i0b = bessel0(beta);
        for (int p = 0; p <= PHASES; p++) {
            double frac = (double) p / PHASES;
            for (int k = 0; k < 2 * half; k++) {
                double x = (k - half + 1) - frac;            // distance from tap to the output point
                double s = x == 0 ? 1 : Math.sin(Math.PI * cutoff * x) / (Math.PI * cutoff * x);
                double r = x / half;
                double w = Math.abs(r) >= 1 ? 0 : bessel0(beta * Math.sqrt(1 - r * r)) / i0b;
                table[p * 2 * half + k] = (float) (cutoff * s * w);
            }
        }
        hist = new float[ch][4096];
        histLen = half - 1;           // leading zeros so the first output is centred on input 0
        pos = half - 1;
    }

    private static double bessel0(double x) {
        double s = 1, t = 1;
        for (int k = 1; k < 50; k++) { t *= (x / (2 * k)) * (x / (2 * k)); s += t; if (t < 1e-12 * s) break; }
        return s;
    }

    @Override public void push(float[][] b, int n) throws Exception {
        if (histLen + n > hist[0].length) {
            int cap = Math.max(histLen + n, hist[0].length * 2);
            for (int c = 0; c < ch; c++) hist[c] = java.util.Arrays.copyOf(hist[c], cap);
        }
        for (int c = 0; c < ch; c++) System.arraycopy(b[c], 0, hist[c], histLen, n);
        histLen += n;
        produce();
    }

    private void produce() throws Exception {
        int maxOut = (int) ((histLen - pos) / step) + 2;
        if (out[0].length < maxOut || out.length != ch) out = new float[ch][Math.max(maxOut, 1024)];
        int k = 0;
        while (pos + half < histLen) {
            int ip = (int) Math.floor(pos);
            double frac = pos - ip;
            int ph = (int) (frac * PHASES);
            double f2 = frac * PHASES - ph;
            int t0 = ph * 2 * half, t1 = t0 + 2 * half;
            int start = ip - half + 1;
            for (int c = 0; c < ch; c++) {
                double acc = 0;
                float[] h = hist[c];
                for (int j = 0; j < 2 * half; j++) {
                    int idx = start + j;
                    if (idx < 0 || idx >= histLen) continue;
                    double w = table[t0 + j] + f2 * (table[t1 + j] - table[t0 + j]);
                    acc += h[idx] * w;
                }
                out[c][k] = (float) acc;
            }
            k++;
            pos += step;
            if (k == out[0].length) { if (next != null) next.push(out, k); k = 0; }
        }
        if (k > 0 && next != null) next.push(out, k);
        // drop consumed history
        int drop = (int) Math.floor(pos) - half - 1;
        if (drop > 0) {
            for (int c = 0; c < ch; c++) System.arraycopy(hist[c], drop, hist[c], 0, histLen - drop);
            histLen -= drop;
            pos -= drop;
        }
    }

    @Override public void end() throws Exception {
        // pad with zeros so the last inputs are fully used
        push(new float[ch][half + 1], half + 1);
        if (next != null) next.end();
    }

    public static float[][] resample(float[][] x, double inRate, double outRate) throws Exception {
        Resampler r = new Resampler(x.length, inRate, outRate);
        float[][] y = Node.apply(x, r, r, x.length);
        int want = (int) Math.round(x[0].length * outRate / inRate);
        float[][] o = new float[x.length][];
        for (int c = 0; c < x.length; c++) o[c] = java.util.Arrays.copyOf(y[c], want);
        return o;
    }
}
