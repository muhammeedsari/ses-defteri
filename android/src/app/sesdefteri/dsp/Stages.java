package app.sesdefteri.dsp;

/** The time-domain repair and dynamics steps. Every step keeps the timeline unchanged. */
public final class Stages {
    private Stages() { }

    /** Picks, sums or drops channels. */
    public static final class ChannelMap extends Node {
        public static final int KEEP = 0, MONO = 1, LEFT = 2, RIGHT = 3, DIFF = 4;
        final int mode, inCh;
        float[][] out = new float[1][0];

        public ChannelMap(int inCh, int mode) { this.inCh = inCh; this.mode = inCh == 1 ? KEEP : mode; }

        public int outChannels() { return mode == KEEP ? inCh : 1; }

        @Override public void push(float[][] b, int n) throws Exception {
            if (mode == KEEP) { next.push(b, n); return; }
            if (out[0].length < n) out = new float[1][n];
            float[] o = out[0];
            for (int i = 0; i < n; i++) {
                float v;
                switch (mode) {
                    case LEFT: v = b[0][i]; break;
                    case RIGHT: v = b[1][i]; break;
                    case DIFF: v = 0.5f * (b[0][i] - b[1][i]); break;
                    default: {
                        float s = 0;
                        for (int c = 0; c < inCh; c++) s += b[c][i];
                        v = s / inCh;
                    }
                }
                o[i] = v;
            }
            next.push(out, n);
        }
    }

    /** Keeps only [start, end) in samples. */
    public static final class Trim extends Node {
        final long start, end;
        long pos;
        float[][] tmp = new float[1][0];

        public Trim(long start, long end) { this.start = start; this.end = end; }

        @Override public void push(float[][] b, int n) throws Exception {
            long a = Math.max(pos, start), z = Math.min(pos + n, end);
            if (z > a) {
                int off = (int) (a - pos), len = (int) (z - a);
                if (off == 0) next.push(b, len);
                else {
                    if (tmp.length != b.length || tmp[0].length < len) tmp = new float[b.length][len];
                    for (int c = 0; c < b.length; c++) System.arraycopy(b[c], off, tmp[c], 0, len);
                    next.push(tmp, len);
                }
            }
            pos += n;
        }
    }

    /** High-pass (rumble, DC) and hum notches. */
    public static final class Filters extends Node {
        final java.util.ArrayList<Biquad> list = new java.util.ArrayList<>();
        final int ch;

        public Filters(int ch, double fs, double hpfHz, double[] notchHz) {
            this.ch = ch;
            if (hpfHz > 0) {
                // 4th-order Butterworth high-pass (two sections)
                list.add(Biquad.highpass(ch, fs, hpfHz, 0.5412));
                list.add(Biquad.highpass(ch, fs, hpfHz, 1.3066));
            }
            if (notchHz != null) for (double f : notchHz) list.add(Biquad.notch(ch, fs, f, 25));
        }

        @Override public void push(float[][] b, int n) throws Exception {
            for (Biquad q : list) for (int c = 0; c < ch; c++) q.process(c, b[c], 0, n);
            next.push(b, n);
        }
    }

    /**
     * Finds short impulsive clicks and rebuilds them from the surrounding audio.
     * A sample is a click when its second difference is far above the local average of the second
     * difference AND far above the local signal level, both measured on both sides (±10 ms).
     * Sharp but natural sounds (consonants, glottal pulses, drums) fail one of the two tests.
     */
    public static final class ClickRepair extends Node {
        final int ch, maxLen, W, DLY;
        final double kD, kX;
        float[][] h;           // history per channel
        double[][] pd, px;     // prefix sums of |d| and x^2 (index j = sum over [0, j))
        long h0;               // absolute index of h[*][0]
        int len;
        long scanned, emitted;
        final long[] regStart, regLast;
        public int repaired;
        float[][] out = new float[1][0];

        public ClickRepair(int ch, double fs, double sensitivity) {
            this.ch = ch;
            maxLen = (int) Math.round(fs * 0.002);
            W = (int) Math.round(fs * 0.010);
            DLY = 2 * W + 64;
            kD = 15 * 10 / sensitivity;
            kX = 4 * 10 / sensitivity;
            h = new float[ch][1 << 15];
            pd = new double[ch][(1 << 15) + 1];
            px = new double[ch][(1 << 15) + 1];
            regStart = new long[ch];
            regLast = new long[ch];
            java.util.Arrays.fill(regStart, -1);
        }

        @Override public void push(float[][] b, int n) throws Exception {
            if (len + n > h[0].length) {
                int cap = Math.max(len + n, h[0].length * 2);
                for (int c = 0; c < ch; c++) {
                    h[c] = java.util.Arrays.copyOf(h[c], cap);
                    pd[c] = java.util.Arrays.copyOf(pd[c], cap + 1);
                    px[c] = java.util.Arrays.copyOf(px[c], cap + 1);
                }
            }
            for (int c = 0; c < ch; c++) System.arraycopy(b[c], 0, h[c], len, n);
            int from = len;
            len += n;
            for (int c = 0; c < ch; c++) sums(c, from);
            scan(false);
            emit(h0 + len - DLY);
        }

        private void sums(int c, int from) {
            float[] x = h[c];
            double[] a = pd[c], q = px[c];
            for (int j = from; j < len; j++) {
                double d = j >= 2 ? x[j] - 2 * x[j - 1] + x[j - 2] : 0;
                a[j + 1] = a[j] + Math.abs(d);
                q[j + 1] = q[j] + (double) x[j] * x[j];
            }
        }

        private double dAbs(int c, int j) { return pd[c][j + 1] - pd[c][j]; }

        private void scan(boolean finalPass) {
            long endAbs = h0 + len - (finalPass ? 0 : W + 16);
            for (long i = Math.max(scanned, h0 + 2); i < endAbs; i++) {
                int j = (int) (i - h0);
                for (int c = 0; c < ch; c++) {
                    double a = dAbs(c, j);
                    boolean hit = false;
                    if (a > 0.01) {
                        int l0 = Math.max(2, j - W), l1 = Math.max(l0, j - 8), r0 = Math.min(len, j + 9), r1 = Math.min(len, j + W + 1);
                        int nl = l1 - l0, nr = r1 - r0;
                        double md = 0, mx = 0;
                        if (nl > 16) { md = Math.max(md, (pd[c][l1] - pd[c][l0]) / nl); mx = Math.max(mx, (px[c][l1] - px[c][l0]) / nl); }
                        if (nr > 16) { md = Math.max(md, (pd[c][r1] - pd[c][r0]) / nr); mx = Math.max(mx, (px[c][r1] - px[c][r0]) / nr); }
                        hit = a > kD * md && a > kX * Math.sqrt(mx);
                    }
                    if (hit) {
                        if (regStart[c] < 0) regStart[c] = i - 1;
                        regLast[c] = i;
                    }
                    if (regStart[c] >= 0 && (i - regLast[c] > 24 || finalPass && i == endAbs - 1)) {
                        long s = regStart[c] - 1, e = regLast[c] + 1;   // samples [s, e] replaced
                        if (e - s + 1 <= maxLen && s - 4 >= h0 && e + 4 < h0 + len) { repair(c, s, e); repaired++; }
                        regStart[c] = -1;
                    }
                }
            }
            scanned = Math.max(scanned, endAbs);
        }

        private float at(int c, long abs) { return h[c][(int) (abs - h0)]; }

        /** least-squares cubic through 4 good samples on each side */
        private void repair(int c, long s, long e) {
            double[] xs = new double[8], ys = new double[8];
            int q = 0;
            for (long i = s - 4; i < s; i++) { xs[q] = i - s; ys[q++] = at(c, i); }
            for (long i = e + 1; i <= e + 4; i++) { xs[q] = i - s; ys[q++] = at(c, i); }
            double[][] A = new double[4][5];
            for (int r = 0; r < 8; r++) {
                double[] p = { 1, xs[r], xs[r] * xs[r], xs[r] * xs[r] * xs[r] };
                for (int i = 0; i < 4; i++) { for (int j = 0; j < 4; j++) A[i][j] += p[i] * p[j]; A[i][4] += p[i] * ys[r]; }
            }
            for (int i = 0; i < 4; i++) {
                int piv = i;
                for (int r = i + 1; r < 4; r++) if (Math.abs(A[r][i]) > Math.abs(A[piv][i])) piv = r;
                double[] t = A[i]; A[i] = A[piv]; A[piv] = t;
                if (Math.abs(A[i][i]) < 1e-12) return;
                for (int r = 0; r < 4; r++) if (r != i) {
                    double f = A[r][i] / A[i][i];
                    for (int j = i; j < 5; j++) A[r][j] -= f * A[i][j];
                }
            }
            double[] co = new double[4];
            for (int i = 0; i < 4; i++) co[i] = A[i][4] / A[i][i];
            for (long i = s; i <= e; i++) {
                double x = i - s;
                h[c][(int) (i - h0)] = (float) (co[0] + co[1] * x + co[2] * x * x + co[3] * x * x * x);
            }
            sums(c, (int) (s - h0));
        }

        private void emit(long upto) throws Exception {
            for (int c = 0; c < ch; c++) if (regStart[c] >= 0) upto = Math.min(upto, regStart[c] - 8);
            upto = Math.min(upto, scanned - 8);
            int cnt = (int) (upto - emitted);
            if (cnt <= 0) return;
            if (out.length != ch || out[0].length < cnt) out = new float[ch][cnt];
            int off = (int) (emitted - h0);
            for (int c = 0; c < ch; c++) System.arraycopy(h[c], off, out[c], 0, cnt);
            emitted += cnt;
            next.push(out, cnt);
            // keep W+16 samples before the emit point as context
            int drop = (int) (emitted - W - 16 - h0);
            if (drop > 8192) {
                for (int c = 0; c < ch; c++) {
                    System.arraycopy(h[c], drop, h[c], 0, len - drop);
                    double bd = pd[c][drop], bx = px[c][drop];
                    for (int j = 0; j <= len - drop; j++) { pd[c][j] = pd[c][j + drop] - bd; px[c][j] = px[c][j + drop] - bx; }
                }
                len -= drop;
                h0 += drop;
            }
        }

        @Override public void end() throws Exception {
            scan(true);
            java.util.Arrays.fill(regStart, -1);
            scanned = h0 + len + 8;
            emit(h0 + len);
            next.end();
        }
    }

    /**
     * Tames low-frequency pops (plosives: "p", "b" bursts into the mic). Zero latency, low band only.
     * A pop is a sudden low-band burst: the fast low-band envelope jumps far above its own slow average
     * and is at least as strong as the rest of the voice. Then only the band below 160 Hz is turned down.
     */
    public static final class PlosiveTamer extends Node {
        final int ch;
        final Biquad lp1, lp2, hp1, hp2;     // Linkwitz-Riley (2nd order) split: hi - lo is flat
        final double atk, rel, sAtk, sRel, gAtk, gRel, ratio, minGain;
        final double[] envL, envH, envS, gain;
        final int[] hold;
        final int holdLen;
        public long active;

        public PlosiveTamer(int ch, double fs, double ratio) {
            this.ch = ch;
            lp1 = new Biquad(ch).setFirstOrder(fs, 160, false);
            lp2 = new Biquad(ch).setFirstOrder(fs, 160, false);
            hp1 = new Biquad(ch).setFirstOrder(fs, 160, true);
            hp2 = new Biquad(ch).setFirstOrder(fs, 160, true);
            atk = 1 - Math.exp(-1 / (0.0005 * fs));
            rel = 1 - Math.exp(-1 / (0.030 * fs));
            sAtk = 1 - Math.exp(-1 / (0.050 * fs));
            sRel = 1 - Math.exp(-1 / (0.300 * fs));
            gAtk = 1 - Math.exp(-1 / (0.0007 * fs));
            gRel = 1 - Math.exp(-1 / (0.060 * fs));
            this.ratio = ratio;
            minGain = Math.pow(10, -24 / 20.0);
            envL = new double[ch]; envH = new double[ch]; envS = new double[ch]; gain = new double[ch];
            hold = new int[ch];
            holdLen = (int) (0.080 * fs);
            java.util.Arrays.fill(gain, 1);
        }

        @Override public void push(float[][] b, int n) throws Exception {
            for (int c = 0; c < ch; c++) {
                float[] x = b[c];
                double eL = envL[c], eH = envH[c], eS = envS[c], g = gain[c];
                for (int i = 0; i < n; i++) {
                    double lo = lp2.tick(c, lp1.tick(c, x[i])), hi = hp2.tick(c, hp1.tick(c, x[i]));
                    double al = Math.abs(lo), ah = Math.abs(hi);
                    eL += (al - eL) * (al > eL ? atk : rel);
                    eH += (ah - eH) * (ah > eH ? atk : rel);
                    eS += (al - eS) * (al > eS ? sAtk : sRel);
                    double want = 1;
                    if (eL > 0.01 && eL > ratio * eH && eL > 6 * eS) hold[c] = holdLen;       // pop starts
                    if (hold[c] > 0) {
                        hold[c]--;
                        if (eL > 0.3 * eH) want = Math.max(minGain, 0.2 * eH / eL);          // while the low band still dominates
                    }
                    g += (want - g) * (want < g ? gAtk : gRel);
                    if (g < 0.9) active++;
                    x[i] = (float) (hi - g * lo);
                }
                envL[c] = eL; envH[c] = eH; envS[c] = eS; gain[c] = g;
            }
            next.push(b, n);
        }
    }

    /** Dynamic de-esser: holds the 5-10 kHz band of "s" sounds down to a level threshold; nothing else is touched. */
    public static final class DeEsser extends Node {
        final int ch;
        final Biquad lp, sideHp, sideLp;
        final double env, gAtk, gRel, thr, minGain;
        final double[] eS, eF, g;
        public long active;

        /** @param thrLin RMS level of the sibilant band above which it is turned down */
        public DeEsser(int ch, double fs, double thrLin, double maxReductionDb) {
            this.ch = ch;
            lp = Biquad.lowpass(ch, fs, 4500, 0.7071);
            sideHp = Biquad.highpass(ch, fs, 5000, 0.7071);
            sideLp = Biquad.lowpass(ch, fs, Math.min(10000, fs * 0.45), 0.7071);
            env = 1 - Math.exp(-1 / (0.004 * fs));
            gAtk = 1 - Math.exp(-1 / (0.0008 * fs));
            gRel = 1 - Math.exp(-1 / (0.040 * fs));
            thr = thrLin;
            minGain = Math.pow(10, -maxReductionDb / 20);
            eS = new double[ch]; eF = new double[ch]; g = new double[ch];
            java.util.Arrays.fill(g, 1);
        }

        @Override public void push(float[][] b, int n) throws Exception {
            double t2 = thr * thr;
            for (int c = 0; c < ch; c++) {
                float[] x = b[c];
                double s2 = eS[c], f2 = eF[c], gg = g[c];
                for (int i = 0; i < n; i++) {
                    double v = x[i];
                    double lo = lp.tick(c, v), hi = v - lo;
                    double sb = sideLp.tick(c, sideHp.tick(c, v));
                    s2 += (sb * sb - s2) * env;
                    f2 += (v * v - f2) * env;
                    double want = 1;
                    if (s2 > t2 && s2 > 0.16 * f2) want = Math.max(minGain, Math.sqrt(t2 / s2));
                    gg += (want - gg) * (want < gg ? gAtk : gRel);
                    if (gg < 0.9) active++;
                    x[i] = (float) (lo + gg * hi);
                }
                eS[c] = s2; eF[c] = f2; g[c] = gg;
            }
            next.push(b, n);
        }
    }

    /** Gentle RMS compressor with soft knee (speech levelling). Linked channels. */
    public static final class Compressor extends Node {
        final int ch;
        final double thrDb, ratio, knee, det, atk, rel;
        double ms, gDb;

        public Compressor(int ch, double fs, double thrDb, double ratio) {
            this.ch = ch;
            this.thrDb = thrDb;
            this.ratio = ratio;
            knee = 6;
            det = 1 - Math.exp(-1 / (0.010 * fs));
            atk = 1 - Math.exp(-1 / (0.008 * fs));
            rel = 1 - Math.exp(-1 / (0.150 * fs));
        }

        @Override public void push(float[][] b, int n) throws Exception {
            for (int i = 0; i < n; i++) {
                double p = 0;
                for (int c = 0; c < ch; c++) p += b[c][i] * b[c][i];
                ms += (p / ch - ms) * det;
                double lvl = 10 * Math.log10(ms + 1e-12), over = lvl - thrDb, red;
                if (over <= -knee / 2) red = 0;
                else if (over >= knee / 2) red = over * (1 - 1 / ratio);
                else { double t = over + knee / 2; red = (1 - 1 / ratio) * t * t / (2 * knee); }
                double want = -red;
                gDb += (want - gDb) * (want < gDb ? atk : rel);
                float gl = (float) Math.pow(10, gDb / 20);
                for (int c = 0; c < ch; c++) b[c][i] *= gl;
            }
            next.push(b, n);
        }
    }

    /** Fixed gain. */
    public static final class Gain extends Node {
        final float g;

        public Gain(double db) { g = (float) Math.pow(10, db / 20); }

        @Override public void push(float[][] b, int n) throws Exception {
            for (float[] c : b) for (int i = 0; i < n; i++) c[i] *= g;
            next.push(b, n);
        }
    }
}
