package app.sesdefteri.dsp;

/**
 * Spectral view and spectral repair. The view is a log-frequency spectrogram (bytes, 0 = quiet, 255 = loud).
 * The repair works on one painted box (time × frequency) of a short region: inside the box every bin is pulled
 * down to the level the same bin has just before and after the box ("blend": the background stays natural) or
 * almost to silence ("mute"). STFT 2048 / hop 512, sqrt-Hann in and out, soft edges.
 */
public final class SpecPaint {
    private SpecPaint() { }

    public static final int N = 2048, HOP = 512;
    public static final double FMIN = 40;

    /** log-spaced band edges between FMIN and min(20 kHz, fs/2) */
    public static double fAt(double row, int rows, double fs) {
        double fmax = Math.min(20000, fs / 2);
        return FMIN * Math.pow(fmax / FMIN, row / rows);
    }

    /** mono x → rows × cols bytes, row 0 = lowest frequency */
    public static byte[] view(float[] x, int len, double fs, int rows, int cols) {
        Fft fft = new Fft(N);
        double[] w = Fft.hann(N, false), re = new double[N], im = new double[N];
        byte[] out = new byte[rows * cols];
        double ref = N * N / 16.0, bin = fs / N;
        for (int c = 0; c < cols; c++) {
            int mid = (int) ((c + 0.5) * len / cols);
            for (int i = 0; i < N; i++) { int j = mid - N / 2 + i; re[i] = j >= 0 && j < len ? x[j] * w[i] : 0; im[i] = 0; }
            fft.transform(re, im, false);
            for (int r = 0; r < rows; r++) {
                double f0 = fAt(r, rows, fs), f1 = fAt(r + 1, rows, fs);
                int a = (int) Math.floor(f0 / bin), z = Math.max(a + 1, (int) Math.ceil(f1 / bin));
                double p = 0;
                for (int k = a; k < z && k < N / 2; k++) p = Math.max(p, re[k] * re[k] + im[k] * im[k]);
                double db = 10 * Math.log10(p / ref + 1e-14);   // 0 dB = full-scale sine
                out[r * cols + c] = (byte) Math.round(Math.max(0, Math.min(255, (db + 110) * 255 / 100)));
            }
        }
        return out;
    }

    /**
     * x: channels × len, the region. Box [s0, s1) samples × [f0, f1] Hz, both inside the region with room around it.
     * Returns the processed region (same size); only [s0 − N, s1 + N) differs from the input.
     */
    public static float[][] repair(float[][] x, int len, double fs, int s0, int s1, double f0, double f1, boolean mute) {
        int ch = x.length, K = N / 2 + 1;
        Fft fft = new Fft(N);
        double[] w = Fft.hann(N, true);
        double bin = fs / N;
        int frames = (len - N) / HOP + 1;
        if (frames < 3) return x;
        // frequency mask with 3-bin soft edges
        double[] fm = new double[K];
        int k0 = (int) Math.floor(f0 / bin), k1 = (int) Math.ceil(f1 / bin);
        for (int k = 0; k < K; k++) {
            double d = k < k0 ? k0 - k : k > k1 ? k - k1 : 0;
            fm[k] = d >= 3 ? 0 : d == 0 ? 1 : 0.5 + 0.5 * Math.cos(Math.PI * d / 3);
        }
        // time mask per frame (by the frame centre) with 2-frame soft edges
        double[] tm = new double[frames];
        for (int t = 0; t < frames; t++) {
            int c = t * HOP + N / 2;
            double d = c < s0 ? (s0 - c) / (double) HOP : c >= s1 ? (c - s1) / (double) HOP + 1e-9 : 0;
            tm[t] = d >= 2 ? 0 : d == 0 ? 1 : 0.5 + 0.5 * Math.cos(Math.PI * d / 2);
        }
        float[][] y = new float[ch][];
        double[] re = new double[N], im = new double[N];
        for (int c = 0; c < ch; c++) {
            // pass 1: magnitudes of every frame (only bins in the mask are needed)
            float[][] mag = new float[frames][];
            double[][] sre = new double[frames][], sim = new double[frames][];
            for (int t = 0; t < frames; t++) {
                int off = t * HOP;
                for (int i = 0; i < N; i++) { re[i] = x[c][off + i] * w[i]; im[i] = 0; }
                fft.transform(re, im, false);
                sre[t] = java.util.Arrays.copyOf(re, K); sim[t] = java.util.Arrays.copyOf(im, K);
                mag[t] = new float[K];
                for (int k = 0; k < K; k++) mag[t][k] = (float) Math.hypot(re[k], im[k]);
            }
            // the background: median of each bin over the frames outside the box (before and after)
            java.util.ArrayList<Integer> outside = new java.util.ArrayList<>();
            for (int t = 0; t < frames; t++) if (tm[t] == 0) outside.add(t);
            double[] bg = new double[K];
            float[] tmp = new float[Math.max(1, outside.size())];
            for (int k = 0; k < K; k++) {
                if (fm[k] == 0 || outside.isEmpty()) continue;
                for (int i = 0; i < outside.size(); i++) tmp[i] = mag[outside.get(i)][k];
                java.util.Arrays.sort(tmp, 0, outside.size());
                bg[k] = tmp[outside.size() / 2];
            }
            // pass 2: gains, inverse, overlap-add (sqrt-Hann² at 75 % overlap sums to 2)
            double[] acc = new double[len];
            for (int t = 0; t < frames; t++) {
                for (int k = 0; k < K; k++) {
                    double m = tm[t] * fm[k], g = 1;
                    if (m > 0) {
                        double target = mute ? 0.02 : Math.min(1, bg[k] / Math.max(1e-12, mag[t][k]));
                        g = 1 - m * (1 - target);
                    }
                    re[k] = sre[t][k] * g; im[k] = sim[t][k] * g;
                    if (k > 0 && k < N / 2) { re[N - k] = re[k]; im[N - k] = -im[k]; }
                }
                fft.transform(re, im, true);
                int off = t * HOP;
                for (int i = 0; i < N; i++) acc[off + i] += re[i] / N * w[i] * 0.5;
            }
            // keep the input outside the zone the box can reach, where the overlap-add is complete
            y[c] = x[c].clone();
            int a = Math.max(N, s0 - N), z = Math.min(len - N, s1 + N);
            for (int i = a; i < z; i++) y[c][i] = (float) acc[i];
        }
        return y;
    }
}
