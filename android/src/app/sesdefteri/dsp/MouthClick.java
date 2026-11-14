package app.sesdefteri.dsp;

/**
 * 4.2: mouth clicks (lips, tongue, saliva) the way voice-over tools do it: the clicks live in the high band, so only
 * the band above 2.5 kHz is looked at and touched. A click is a short (≤ 8 ms) burst in that band that stands far above
 * the band's own level in the ±15 ms around it (the burst itself left out); "s" sounds and breaths are long and fail
 * the test. The burst's high band is lowered to the surrounding level with 1 ms ramps; the voice below 2.5 kHz is
 * never changed, so clean speech comes out the same.
 */
public final class MouthClick extends Node {
    final int ch, W, C, gap, maxLen, ramp, pad;
    final double k, kr;
    final Biquad h1, h2;
    float[][] x, hc; float[] hm, g; double[] ps;
    long b0, sc, gp;   // b0: absolute index of slot 0; sc: next sample to scan; gp: next sample to emit
    int len;
    long runS = -1, runE; double runPk, runBg; int runN; boolean runLong;
    public int repaired;
    float[][] out = new float[1][0];

    public MouthClick(int ch, double fs, double strength) {
        this.ch = ch;
        W = (int) (fs * 0.020); C = (int) (fs * 0.005); gap = (int) (fs * 0.001); maxLen = (int) (fs * 0.008); ramp = (int) (fs * 0.001); pad = (int) (fs * 0.0015);
        k = 7 / Math.max(0.3, strength); kr = 1.5;
        h1 = Biquad.highpass(ch, fs, 2500, 0.5412); h2 = Biquad.highpass(ch, fs, 2500, 1.3066);
        int cap = 1 << 15;
        x = new float[ch][cap]; hc = new float[ch][cap]; hm = new float[cap]; g = new float[cap]; ps = new double[cap + 1];
    }

    @Override public void push(float[][] b, int n) throws Exception {
        if (len + n > hm.length) {
            int cap = Math.max(len + n, hm.length * 2);
            for (int c = 0; c < ch; c++) { x[c] = java.util.Arrays.copyOf(x[c], cap); hc[c] = java.util.Arrays.copyOf(hc[c], cap); }
            hm = java.util.Arrays.copyOf(hm, cap); g = java.util.Arrays.copyOf(g, cap); ps = java.util.Arrays.copyOf(ps, cap + 1);
        }
        for (int c = 0; c < ch; c++) {
            System.arraycopy(b[c], 0, x[c], len, n);
            System.arraycopy(b[c], 0, hc[c], len, n);
            h1.process(c, hc[c], len, n); h2.process(c, hc[c], len, n);
        }
        for (int i = len; i < len + n; i++) {
            float v = 0; for (int c = 0; c < ch; c++) v += hc[c][i];
            v /= ch; hm[i] = v; g[i] = 1;
            ps[i + 1] = ps[i] + (double) v * v;
        }
        len += n;
        scan(b0 + len - W - 1);
        emit(Math.min(sc - ramp - pad - 1, runS >= 0 ? runS - ramp - pad : Long.MAX_VALUE));
    }

    private void scan(long to) {
        for (; sc < to; sc++) {
            int j = (int) (sc - b0);
            double a = Math.abs(hm[j]);
            boolean hit = false;
            double bg = 0;
            if (a > 0.002) {
                int l0 = Math.max(0, j - W), l1 = Math.max(l0, j - C), r0 = Math.min(len, j + C + 1), r1 = Math.min(len, j + W + 1);
                int nn = (l1 - l0) + (r1 - r0);
                if (nn > 0) {
                    bg = Math.sqrt(((ps[l1] - ps[l0]) + (ps[r1] - ps[r0])) / nn);
                    hit = a > k * bg;
                }
            }
            if (hit) {
                if (runS < 0) { runS = sc; runPk = 0; runBg = 0; runN = 0; runLong = false; }
                runE = sc; runPk = Math.max(runPk, a); runBg += bg; runN++;
                if (runE - runS > maxLen) runLong = true;
            } else if (runS >= 0 && sc - runE > gap) close();
        }
    }

    private void close() {
        if (!runLong) {
            double gr = Math.min(1, kr * (runBg / runN) / runPk);
            // the detector sees only the peaks: the click's soft start and tail around them go too
            long rs = runS - pad, re = runE + pad, s = rs - ramp, e = re + ramp;
            for (long i = Math.max(s, gp); i <= e && i < b0 + len; i++) {
                double d = i < rs ? (double) (rs - i) / ramp : i > re ? (double) (i - re) / ramp : 0;
                double v = gr + (1 - gr) * d;
                int q = (int) (i - b0);
                if (v < g[q]) g[q] = (float) v;
            }
            repaired++;
        }
        runS = -1;
    }

    private void emit(long upto) throws Exception {
        upto = Math.min(upto, b0 + len);
        int cnt = (int) (upto - gp);
        if (cnt <= 0) return;
        if (out.length != ch || out[0].length < cnt) out = new float[ch][cnt];
        int o = (int) (gp - b0);
        for (int c = 0; c < ch; c++) {
            float[] xc = x[c], hh = hc[c], oc = out[c];
            for (int i = 0; i < cnt; i++) oc[i] = xc[o + i] - hh[o + i] * (1 - g[o + i]);
        }
        gp += cnt;
        next.push(out, cnt);
        int drop = (int) (gp - W - 1 - b0);
        if (drop > 1 << 15) {
            for (int c = 0; c < ch; c++) { System.arraycopy(x[c], drop, x[c], 0, len - drop); System.arraycopy(hc[c], drop, hc[c], 0, len - drop); }
            System.arraycopy(hm, drop, hm, 0, len - drop); System.arraycopy(g, drop, g, 0, len - drop);
            double base = ps[drop];
            for (int j = 0; j <= len - drop; j++) ps[j] = ps[j + drop] - base;
            len -= drop; b0 += drop;
        }
    }

    @Override public void end() throws Exception {
        scan(b0 + len);
        if (runS >= 0) close();
        emit(b0 + len);
        if (next != null) next.end();
    }
}
