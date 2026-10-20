package app.sesdefteri.dsp;

/** In-place radix-2 complex FFT. No Android dependencies (runs on any JVM for tests). */
public final class Fft {
    public final int n;
    private final int[] rev;
    private final double[] cos, sin;

    public Fft(int n) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("power of two");
        this.n = n;
        rev = new int[n];
        int bits = Integer.numberOfTrailingZeros(n);
        for (int i = 0; i < n; i++) rev[i] = Integer.reverse(i) >>> (32 - bits);
        cos = new double[n / 2];
        sin = new double[n / 2];
        for (int i = 0; i < n / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / n);
            sin[i] = Math.sin(-2 * Math.PI * i / n);
        }
    }

    /** forward (inverse=false) or inverse (unscaled) transform */
    public void transform(double[] re, double[] im, boolean inverse) {
        for (int i = 0; i < n; i++) {
            int j = rev[i];
            if (j > i) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1, step = n / size;
            for (int i = 0; i < n; i += size) {
                for (int k = 0; k < half; k++) {
                    double wr = cos[k * step], wi = inverse ? -sin[k * step] : sin[k * step];
                    int a = i + k, b = a + half;
                    double xr = re[b] * wr - im[b] * wi, xi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                }
            }
        }
    }

    public static int nextPow2(int v) {
        int p = 1;
        while (p < v) p <<= 1;
        return p;
    }

    public static double[] hann(int n, boolean sqrt) {
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            double v = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / n);
            w[i] = sqrt ? Math.sqrt(v) : v;
        }
        return w;
    }
}
