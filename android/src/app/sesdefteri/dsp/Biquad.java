package app.sesdefteri.dsp;

/** Second-order IIR section (RBJ cookbook), one state per channel. Direct form II transposed, double precision. */
public final class Biquad {
    double b0, b1, b2, a1, a2;
    private final double[] z1, z2;

    public Biquad(int channels) {
        z1 = new double[channels];
        z2 = new double[channels];
        b0 = 1;
    }

    public static Biquad lowpass(int ch, double fs, double f, double q) { return new Biquad(ch).setLowpass(fs, f, q); }
    public static Biquad highpass(int ch, double fs, double f, double q) { return new Biquad(ch).setHighpass(fs, f, q); }
    public static Biquad bandpass(int ch, double fs, double f, double q) { return new Biquad(ch).setBandpass(fs, f, q); }
    public static Biquad peaking(int ch, double fs, double f, double q, double db) { return new Biquad(ch).setPeaking(fs, f, q, db); }
    public static Biquad notch(int ch, double fs, double f, double q) { return new Biquad(ch).setNotch(fs, f, q); }

    private Biquad norm(double a0, double nb0, double nb1, double nb2, double na1, double na2) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0;
        return this;
    }

    public Biquad setLowpass(double fs, double f, double q) {
        double w = 2 * Math.PI * Math.min(f, fs * 0.49) / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
        return norm(1 + al, (1 - c) / 2, 1 - c, (1 - c) / 2, -2 * c, 1 - al);
    }

    public Biquad setHighpass(double fs, double f, double q) {
        double w = 2 * Math.PI * Math.min(f, fs * 0.49) / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
        return norm(1 + al, (1 + c) / 2, -(1 + c), (1 + c) / 2, -2 * c, 1 - al);
    }

    public Biquad setBandpass(double fs, double f, double q) {
        double w = 2 * Math.PI * Math.min(f, fs * 0.49) / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
        return norm(1 + al, al, 0, -al, -2 * c, 1 - al);
    }

    public Biquad setPeaking(double fs, double f, double q, double db) {
        double A = Math.pow(10, db / 40), w = 2 * Math.PI * Math.min(f, fs * 0.49) / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
        return norm(1 + al / A, 1 + al * A, -2 * c, 1 - al * A, -2 * c, 1 - al / A);
    }

    /** RBJ high shelf (same formulas pyloudnorm uses for its K-weighting stage) */
    public Biquad setHighShelf(double fs, double f, double q, double db) {
        double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q), sA = Math.sqrt(A);
        return norm((A + 1) - (A - 1) * c + 2 * sA * al,
                A * ((A + 1) + (A - 1) * c + 2 * sA * al), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - 2 * sA * al),
                2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - 2 * sA * al);
    }

    public Biquad setLowShelf(double fs, double f, double q, double db) {
        double A = Math.pow(10, db / 40), w = 2 * Math.PI * f / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q), sA = Math.sqrt(A);
        return norm((A + 1) + (A - 1) * c + 2 * sA * al,
                A * ((A + 1) - (A - 1) * c + 2 * sA * al), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - 2 * sA * al),
                -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - 2 * sA * al);
    }

    /** first-order low/high-pass (bilinear), stored as a biquad with b2 = a2 = 0 */
    public Biquad setFirstOrder(double fs, double f, boolean high) {
        double K = Math.tan(Math.PI * f / fs);
        double a = (K - 1) / (K + 1);
        if (high) return norm(1, 1 / (1 + K), -1 / (1 + K), 0, a, 0);
        return norm(1, K / (1 + K), K / (1 + K), 0, a, 0);
    }

    public void reset() { java.util.Arrays.fill(z1, 0); java.util.Arrays.fill(z2, 0); }

    public Biquad setNotch(double fs, double f, double q) {
        double w = 2 * Math.PI * Math.min(f, fs * 0.49) / fs, c = Math.cos(w), al = Math.sin(w) / (2 * q);
        return norm(1 + al, 1, -2 * c, 1, -2 * c, 1 - al);
    }

    public double tick(int ch, double x) {
        double y = b0 * x + z1[ch];
        z1[ch] = b1 * x - a1 * y + z2[ch];
        z2[ch] = b2 * x - a2 * y;
        return y;
    }

    public void process(int ch, float[] buf, int off, int n) {
        double s1 = z1[ch], s2 = z2[ch];
        for (int i = off; i < off + n; i++) {
            double x = buf[i];
            double y = b0 * x + s1;
            s1 = b1 * x - a1 * y + s2;
            s2 = b2 * x - a2 * y;
            buf[i] = (float) y;
        }
        z1[ch] = s1; z2[ch] = s2;
    }

    /** magnitude response in dB at frequency f */
    public double responseDb(double fs, double f) {
        double w = 2 * Math.PI * f / fs;
        double cr = Math.cos(w), ci = -Math.sin(w), c2r = Math.cos(2 * w), c2i = -Math.sin(2 * w);
        double nr = b0 + b1 * cr + b2 * c2r, ni = b1 * ci + b2 * c2i;
        double dr = 1 + a1 * cr + a2 * c2r, di = a1 * ci + a2 * c2i;
        return 10 * Math.log10((nr * nr + ni * ni) / (dr * dr + di * di));
    }
}
