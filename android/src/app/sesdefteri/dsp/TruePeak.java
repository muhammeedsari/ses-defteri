package app.sesdefteri.dsp;

/** True-peak estimation by 4x oversampling (BS.1770 Annex 2 style). */
public final class TruePeak {
    static final int T = 12, D = 6;             // taps per phase, lookahead in samples
    static final double[][] TAPS = new double[3][T];

    static {
        double beta = 7.0, i0 = bessel0(beta);
        for (int j = 1; j <= 3; j++) {
            double f = j / 4.0;
            for (int k = 0; k < T; k++) {
                int i = k - (T / 2 - 1);            // -5 .. 6
                double x = f - i;
                double s = Math.sin(Math.PI * x) / (Math.PI * x);
                double r = x / (T / 2.0 + 0.5);
                double w = Math.abs(r) >= 1 ? 0 : bessel0(beta * Math.sqrt(1 - r * r)) / i0;
                TAPS[j - 1][k] = s * w;
            }
        }
    }

    static double bessel0(double x) {
        double s = 1, t = 1;
        for (int k = 1; k < 50; k++) { t *= (x / (2 * k)) * (x / (2 * k)); s += t; if (t < 1e-14 * s) break; }
        return s;
    }

    /** interpolated value at m + j/4 from samples x[m-5..m+6] given by accessor */
    static double interp(float[] x, int m, int j) {
        double acc = 0;
        double[] h = TAPS[j - 1];
        for (int k = 0; k < T; k++) {
            int idx = m + k - (T / 2 - 1);
            if (idx >= 0 && idx < x.length) acc += x[idx] * h[k];
        }
        return acc;
    }

    /** max true peak (linear) of a whole signal */
    public static double measure(float[][] x) {
        double mx = 0;
        for (float[] c : x) {
            for (int m = 0; m < c.length; m++) {
                double a = Math.abs(c[m]);
                if (a > mx) mx = a;
                for (int j = 1; j <= 3; j++) {
                    double v = Math.abs(interp(c, m, j));
                    if (v > mx) mx = v;
                }
            }
        }
        return mx;
    }

    public static double db(double lin) { return 20 * Math.log10(Math.max(lin, 1e-12)); }
}
