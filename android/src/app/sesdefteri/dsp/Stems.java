package app.sesdefteri.dsp;

/**
 * 3.8: splits music without its voice into drums, bass and everything else, so "4 parts" = voice + these three.
 * Harmonic/percussive median filtering (short time median = steady notes, short frequency median = hits),
 * the steady part below ~200 Hz is the bass. The three masks add up to 1, so the parts add up to the input.
 * Streaming: STFT 2048 / hop 512 with sqrt-Hann windows; the mask is computed once from the channel average.
 */
public final class Stems extends Node {
    static final int N = 2048, HOP = 512, L = 9, M = L / 2;
    final int ch, bins = N / 2 + 1;
    final Fft fft = new Fft(N);
    final double[] win = new double[N], low = new double[N / 2 + 1];
    final float[][] buf;
    final double[][][] re, im;
    final double[][] mag;
    final double[][][] ola;
    final Node[] outs;
    final double[] r = new double[N], i = new double[N], tmp = new double[L], fm = new double[L], pm, hm;
    final float[][] emit;
    int fill;
    long frames, real, sent;
    boolean flushing;

    public Stems(int ch, double fs, Node drums, Node bass, Node other) {
        this.ch = ch;
        outs = new Node[] { drums, bass, other };
        for (int k = 0; k < N; k++) win[k] = Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * k / N));
        for (int k = 0; k < bins; k++) { double f = k * fs / N; low[k] = f <= 150 ? 1 : f >= 250 ? 0 : (250 - f) / 100; }
        buf = new float[ch][N];
        re = new double[L][ch][bins]; im = new double[L][ch][bins]; mag = new double[L][bins];
        ola = new double[3][ch][N];
        pm = new double[bins]; hm = new double[bins];
        emit = new float[ch][HOP];
    }

    private static double median(double[] a, int n) {
        for (int x = 1; x < n; x++) { double v = a[x]; int y = x - 1; while (y >= 0 && a[y] > v) { a[y + 1] = a[y]; y--; } a[y + 1] = v; }
        return a[n / 2];
    }

    @Override public void push(float[][] b, int n) throws Exception {
        for (int s = 0; s < n; s++) {
            for (int c = 0; c < ch; c++) buf[c][N - HOP + fill] = b[c][s];
            if (!flushing) real++;
            if (++fill == HOP) step();
        }
    }

    private void step() throws Exception {
        int slot = (int) (frames % L);
        java.util.Arrays.fill(mag[slot], 0);
        for (int c = 0; c < ch; c++) {
            for (int k = 0; k < N; k++) { r[k] = buf[c][k] * win[k]; i[k] = 0; }
            fft.transform(r, i, false);
            for (int k = 0; k < bins; k++) { re[slot][c][k] = r[k]; im[slot][c][k] = i[k]; mag[slot][k] += Math.hypot(r[k], i[k]) / ch; }
            System.arraycopy(buf[c], HOP, buf[c], 0, N - HOP);
        }
        fill = 0;
        frames++;
        if (frames > M) synth((int) ((frames - 1 - M) % L), (int) Math.min(frames, L));
    }

    private void synth(int cs, int have) throws Exception {
        double[] cm = mag[cs];
        for (int k = 0; k < bins; k++) {
            for (int t = 0; t < have; t++) tmp[t] = mag[t][k];
            hm[k] = median(tmp, have);
            int a = Math.max(0, k - M), z = Math.min(bins - 1, k + M), q = 0;
            for (int x = a; x <= z; x++) fm[q++] = cm[x];
            pm[k] = median(fm, q);
        }
        for (int st = 0; st < 3; st++) {
            for (int c = 0; c < ch; c++) {
                for (int k = 0; k < bins; k++) {
                    double h2 = hm[k] * hm[k], p2 = pm[k] * pm[k], pp = p2 / (h2 + p2 + 1e-12);
                    double g = st == 0 ? pp : st == 1 ? (1 - pp) * low[k] : (1 - pp) * (1 - low[k]);
                    r[k] = re[cs][c][k] * g; i[k] = im[cs][c][k] * g;
                    if (k > 0 && k < N / 2) { r[N - k] = r[k]; i[N - k] = -i[k]; }
                }
                fft.transform(r, i, true);
                double[] o = ola[st][c];
                for (int k = 0; k < N; k++) o[k] += r[k] * win[k] * 0.5 / N;
            }
        }
        // the first HOP samples of the accumulators are final now: absolute positions [base, base + HOP)
        long base = (frames - M) * (long) HOP - N, lo = Math.max(base, sent), hi = Math.min(base + HOP, real);
        int cnt = (int) Math.max(0, hi - lo), from = (int) (lo - base);
        for (int st = 0; st < 3; st++) {
            if (cnt > 0) {
                for (int c = 0; c < ch; c++) for (int k = 0; k < cnt; k++) emit[c][k] = (float) ola[st][c][from + k];
                outs[st].push(emit, cnt);
            }
            for (int c = 0; c < ch; c++) { double[] o = ola[st][c]; System.arraycopy(o, HOP, o, 0, N - HOP); java.util.Arrays.fill(o, N - HOP, N, 0); }
        }
        if (cnt > 0) sent = hi;
    }

    @Override public void end() throws Exception {
        flushing = true;
        float[][] z = new float[ch][HOP];
        while (sent < real) push(z, HOP);   // zeros push the last frames out; they do not count as input
        for (Node o : outs) o.end();
    }
}
