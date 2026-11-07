package app.sesdefteri.dsp;

/**
 * 4.2: "learn the noise here" the way Audacity's Noise Reduction does it. A noise profile (mean power per bin of a
 * stretch that is only noise) sets a threshold per bin; a bin is kept when the second-loudest of 5 neighbouring frames
 * (≈50 ms) rises above it, otherwise it is lowered by the chosen amount. Gains open slightly early (attack 20 ms,
 * look-ahead), close slowly (release 100 ms) and are smoothed across frequency, so speech keeps its edges and the
 * leftover noise doesn't warble. 43 ms frames (2048 at 48 kHz) catch hums and whines a short frame misses.
 * Output is sample-aligned with the input.
 */
public final class NoiseGate extends Node {
    static final int D = 4;   // look-ahead in frames: 2 for the decision window, 2 for the attack
    final int ch, N, hop, bins, P;
    final Fft fft;
    final double[] win, thr, re, im, gs, lg;
    final float[][] in;
    final double[][] ola, gPrev;
    final double[][][] sre, sim, pw, cls;   // [ch][ring 5][bins]
    final double floorG, atk, rel;
    final int smooth;
    int fill;
    long received, emitted, total = -1, f;
    float[][] out;

    public static int size(int fs) { return Fft.nextPow2((int) Math.round(fs * 0.043)); }

    /** collects the noise profile: mean power per bin (same window and size as the gate) */
    public static final class Learner extends Node {
        final int N; final Fft fft; final double[] win, re, im; public final double[] mean;
        final float[] buf; int fill; long frames; double sq; long n;
        public Learner(int fs) {
            N = size(fs); fft = new Fft(N); win = Fft.hann(N, true);
            re = new double[N]; im = new double[N]; mean = new double[N / 2 + 1]; buf = new float[N];
        }
        @Override public void push(float[][] b, int cnt) {
            for (int i = 0; i < cnt; i++) {
                float v = 0; for (float[] c : b) v += c[i]; v /= b.length;
                sq += v * v; n++;
                buf[fill++] = v;
                if (fill == N) {
                    for (int k = 0; k < N; k++) { re[k] = buf[k] * win[k]; im[k] = 0; }
                    fft.transform(re, im, false);
                    for (int k = 0; k < mean.length; k++) mean[k] += re[k] * re[k] + im[k] * im[k];
                    frames++;
                    System.arraycopy(buf, N / 2, buf, 0, N / 2); fill = N / 2;
                }
            }
        }
        @Override public void end() { }
        /** null when the stretch was too short */
        public double[] profile() {
            if (frames < 2) return null;
            double[] p = mean.clone();
            for (int k = 0; k < p.length; k++) p[k] /= frames;
            return p;
        }
        public double rmsDb() { return 10 * Math.log10(Math.max(1e-12, sq / Math.max(1, n))); }
    }

    public NoiseGate(int channels, int fs, double[] profile, double reductionDb, double sensitivityDb) {
        ch = channels;
        N = size(fs); hop = N / 4; bins = N / 2 + 1; P = N - hop;
        fft = new Fft(N);
        win = Fft.hann(N, true);
        thr = new double[bins];
        double s = Math.pow(10, sensitivityDb / 10);
        for (int k = 0; k < bins; k++) thr[k] = profile[k] * s + 1e-20;
        re = new double[N]; im = new double[N]; gs = new double[bins]; lg = new double[bins];
        in = new float[ch][N]; ola = new double[ch][N]; gPrev = new double[ch][bins];
        sre = new double[ch][D + 1][bins]; sim = new double[ch][D + 1][bins]; pw = new double[ch][D + 1][bins]; cls = new double[ch][D + 1][bins];
        floorG = Math.pow(10, -reductionDb / 20);
        double hs = (double) hop / fs;
        atk = Math.pow(10, -reductionDb * hs / 0.02 / 20);
        rel = Math.pow(10, -reductionDb * hs / 0.10 / 20);
        smooth = 3;
        for (double[] a : gPrev) java.util.Arrays.fill(a, floorG);
        for (double[][] a : cls) for (double[] b : a) java.util.Arrays.fill(b, floorG);
        out = new float[ch][hop];
        fill = P;
    }

    @Override public void push(float[][] b, int n) throws Exception {
        int off = 0;
        while (off < n) {
            int take = Math.min(n - off, N - fill);
            for (int c = 0; c < ch; c++) System.arraycopy(b[c], off, in[c], fill, take);
            fill += take; off += take; received += take;
            if (fill == N) frame();
        }
    }

    private void frame() throws Exception {
        int R = D + 1, cur = (int) (f % R), dec = (int) ((f + R - 2) % R), o = (int) ((f + 1) % R), o1 = (int) ((f + 2) % R);
        for (int c = 0; c < ch; c++) {
            float[] x = in[c];
            for (int i = 0; i < N; i++) { re[i] = x[i] * win[i]; im[i] = 0; }
            fft.transform(re, im, false);
            double[] sr = sre[c][cur], si = sim[c][cur], p = pw[c][cur];
            for (int k = 0; k < bins; k++) { sr[k] = re[k]; si[k] = im[k]; p[k] = re[k] * re[k] + im[k] * im[k]; }
            // decision for frame f-2: second-loudest of frames f-4..f
            double[][] pc = pw[c];
            double[] cd = cls[c][dec];
            for (int k = 0; k < bins; k++) {
                double m1 = 0, m2 = 0;
                for (int r = 0; r < R; r++) { double v = pc[r][k]; if (v > m1) { m2 = m1; m1 = v; } else if (v > m2) m2 = v; }
                cd[k] = m2 > thr[k] ? 1 : floorG;
            }
            // frame f-4 goes out: opens early for what comes in the next two frames, closes slowly
            double[] c0 = cls[c][o], c1 = cls[c][o1], gp = gPrev[c];
            for (int k = 0; k < bins; k++) {
                double g = Math.max(c0[k], Math.max(c1[k] * atk, cd[k] * atk * atk));
                g = Math.max(g, gp[k] * rel);
                if (g > 1) g = 1;
                gp[k] = g;
                lg[k] = Math.log(g);
            }
            for (int k = 0; k < bins; k++) {
                int a = Math.max(0, k - smooth), z = Math.min(bins - 1, k + smooth);
                double sum = 0; for (int j = a; j <= z; j++) sum += lg[j];
                gs[k] = Math.exp(sum / (z - a + 1));
            }
            double[] orr = sre[c][o], oi = sim[c][o];
            for (int k = 0; k < bins; k++) {
                re[k] = orr[k] * gs[k]; im[k] = oi[k] * gs[k];
                if (k > 0 && k < N / 2) { re[N - k] = re[k]; im[N - k] = -im[k]; }
            }
            fft.transform(re, im, true);
            double[] ol = ola[c];
            double sc = 0.5 / N;
            for (int i = 0; i < N; i++) ol[i] += re[i] * win[i] * sc;
        }
        long firstOrig = (f - D) * hop - P;
        f++;
        int skip = 0, cnt = hop;
        if (firstOrig < 0) skip = (int) Math.min(hop, -firstOrig);
        if (total >= 0) cnt = (int) Math.max(0, Math.min(hop, total - firstOrig));
        int k = 0;
        for (int i = skip; i < cnt; i++, k++) for (int c = 0; c < ch; c++) out[c][k] = (float) ola[c][i];
        for (int c = 0; c < ch; c++) {
            System.arraycopy(ola[c], hop, ola[c], 0, N - hop);
            java.util.Arrays.fill(ola[c], N - hop, N, 0);
            System.arraycopy(in[c], hop, in[c], 0, N - hop);
        }
        fill = N - hop;
        if (k > 0) { emitted += k; if (next != null) next.push(out, k); }
    }

    @Override public void end() throws Exception {
        total = received;
        float[][] z = new float[ch][hop];
        while (emitted < total) push(z, hop);
        if (next != null) next.end();
    }
}
