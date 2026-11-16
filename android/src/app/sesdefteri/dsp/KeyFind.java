package app.sesdefteri.dsp;

/**
 * 4.6: the key of a piece of music (e.g. A minor). Pitch-class energy (chroma) from 8192-point spectra between
 * 60 Hz and 2 kHz, matched against the Krumhansl–Kessler major / minor key profiles in all 12 transpositions.
 * Passes the audio on unchanged, so it can sit in front of another analyser.
 */
public final class KeyFind extends Node {
    static final int N = 8192, HOP = 4096;
    static final double[] MAJ = { 6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88 };
    static final double[] MIN = { 6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17 };
    final Fft fft = new Fft(N);
    final double[] win = Fft.hann(N, false), re = new double[N], im = new double[N], chroma = new double[12];
    final int[] pc;
    final float[] buf = new float[N];
    int fill;
    public int key = -1;
    public boolean minor;
    public double confidence;

    public KeyFind(double fs) {
        pc = new int[N / 2];
        for (int k = 0; k < N / 2; k++) {
            double f = k * fs / N;
            pc[k] = f < 60 || f > 2000 ? -1 : (int) Math.floorMod(Math.round(12 * Math.log(f / 440.0) / Math.log(2)) + 9L, 12L);   // 0 = C
        }
    }

    @Override public void push(float[][] b, int n) throws Exception {
        int ch = b.length;
        for (int i = 0; i < n; i++) {
            float s = 0; for (int c = 0; c < ch; c++) s += b[c][i];
            buf[fill++] = s / ch;
            if (fill == N) { frame(); System.arraycopy(buf, HOP, buf, 0, N - HOP); fill = N - HOP; }
        }
        if (next != null) next.push(b, n);
    }

    private void frame() {
        for (int k = 0; k < N; k++) { re[k] = buf[k] * win[k]; im[k] = 0; }
        fft.transform(re, im, false);
        double[] c = new double[12]; double tot = 0;
        for (int k = 1; k < N / 2; k++) { if (pc[k] < 0) continue; double m = Math.sqrt(Math.hypot(re[k], im[k])); c[pc[k]] += m; tot += m; }
        if (tot > 1e-6) for (int q = 0; q < 12; q++) chroma[q] += c[q] / tot;   // each frame counts the same, loud or soft
    }

    private static double corr(double[] x, double[] prof, int shift) {
        double mx = 0, mp = 0; for (int i = 0; i < 12; i++) { mx += x[i]; mp += prof[i]; } mx /= 12; mp /= 12;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < 12; i++) { double a = x[(i + shift) % 12] - mx, b = prof[i] - mp; sxy += a * b; sxx += a * a; syy += b * b; }
        return sxx * syy > 0 ? sxy / Math.sqrt(sxx * syy) : 0;
    }

    @Override public void end() throws Exception {
        double best = -2, second = -2;
        for (int s = 0; s < 12; s++) for (int m = 0; m < 2; m++) {
            double r = corr(chroma, m == 0 ? MAJ : MIN, s);
            if (r > best) { second = best; best = r; key = s; minor = m == 1; } else if (r > second) second = r;
        }
        confidence = Math.max(0, Math.min(1, (best - second) * 5 + (best - 0.5)));
        if (next != null) next.end();
    }
}
