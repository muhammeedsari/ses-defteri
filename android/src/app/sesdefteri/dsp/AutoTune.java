package app.sesdefteri.dsp;

/**
 * Pitch correction (3.3): the sung pitch is tracked (YIN on a 12 kHz copy, every 5 ms), every voiced moment is pulled
 * to the nearest note of the chosen scale, and the voice is re-pitched with TD-PSOLA (pitch-synchronous grains), so the
 * formants stay where they were. "speed" is the retune time: 0 = instant (the robotic effect), larger = more natural.
 * Works in 8 s blocks with a little context and a 10 ms crossfade, so memory stays small for long recordings.
 */
public final class AutoTune extends Node {
    static final double HOP_S = 0.005, CHUNK_S = 8, CTX_S = 0.08, XF_S = 0.01;
    final int ch, fs, hop, chunk, ctx, xf;
    final boolean[] allowed = new boolean[12];
    final double tau, amount;
    float[][] buf;
    long bufStart, received, emitted;
    int bufLen;
    float[][] tail;                  // the next chunk's first xf samples as the previous chunk saw them
    double note = -1;                // smoothed target note, carried across chunks
    public double corrected;         // seconds of voice that were moved

    /** key 0 = C … 11 = B; scale "major", "minor" or "chromatic"; speed seconds; amount 0..1 */
    public AutoTune(int ch, int fs, int key, String scale, double speed, double amount) {
        this.ch = ch; this.fs = fs;
        hop = (int) Math.round(fs * HOP_S); chunk = (int) (fs * CHUNK_S); ctx = (int) (fs * CTX_S); xf = (int) (fs * XF_S);
        int[] steps = "minor".equals(scale) ? new int[] { 0, 2, 3, 5, 7, 8, 10 } : "chromatic".equals(scale) ? new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 } : new int[] { 0, 2, 4, 5, 7, 9, 11 };
        for (int s : steps) allowed[(key + s) % 12] = true;
        tau = Math.max(0, speed);
        this.amount = Math.max(0, Math.min(1, amount));
        buf = new float[ch][chunk + 2 * ctx + 2 * xf + 8192];
    }

    @Override public void push(float[][] b, int n) throws Exception {
        if (bufLen + n > buf[0].length) {
            int cap = Math.max(bufLen + n, buf[0].length * 2);
            for (int c = 0; c < ch; c++) buf[c] = java.util.Arrays.copyOf(buf[c], cap);
        }
        for (int c = 0; c < ch; c++) System.arraycopy(b[c], 0, buf[c], bufLen, n);
        bufLen += n;
        received += n;
        while (received - emitted >= chunk + ctx + xf) block(emitted + chunk, false);
    }

    @Override public void end() throws Exception {
        while (emitted < received) block(Math.min(received, emitted + chunk), true);
        if (next != null) next.end();
    }

    /** outputs [emitted, e) from a PSOLA run over [emitted − ctx, e + ctx + xf) */
    private void block(long e, boolean last) throws Exception {
        long r0 = Math.max(bufStart, emitted - ctx), r1 = Math.min(received, e + ctx + xf);
        int n = (int) (r1 - r0), off = (int) (r0 - bufStart);
        float[][] x = new float[ch][n];
        for (int c = 0; c < ch; c++) System.arraycopy(buf[c], off, x[c], 0, n);
        float[][] y = process(x, n);
        int s = (int) (emitted - r0), m = (int) (e - emitted);
        float[][] o = new float[ch][m];
        for (int c = 0; c < ch; c++) {
            System.arraycopy(y[c], s, o[c], 0, m);
            if (tail != null) for (int i = 0; i < Math.min(xf, m); i++) { double w = (i + 0.5) / xf; o[c][i] = (float) (tail[c][i] * (1 - w) + o[c][i] * w); }
        }
        int tl = Math.min(xf, n - (s + m));
        if (!last && tl == xf) { tail = new float[ch][xf]; for (int c = 0; c < ch; c++) System.arraycopy(y[c], s + m, tail[c], 0, xf); }
        else tail = null;
        emitted = e;
        // keep ctx samples before the next start
        int drop = (int) (Math.max(bufStart, emitted - ctx) - bufStart);
        if (drop > 0) { for (int c = 0; c < ch; c++) System.arraycopy(buf[c], drop, buf[c], 0, bufLen - drop); bufLen -= drop; bufStart += drop; }
        if (next != null) next.push(o, m);
    }

    /* ---------- pitch track ---------- */

    /** f0 in Hz every hop samples of x (0 = unvoiced); mono, at fs */
    static double[] track(float[] x, int n, int fs, int hop) {
        int D = Math.max(1, (int) Math.round(fs / 12000.0));
        double fd = fs / (double) D;
        int nd = n / D;
        float[] d = new float[nd];
        for (int i = 0; i < nd; i++) { float s = 0; for (int k = 0; k < D; k++) s += x[i * D + k]; d[i] = s / D; }
        int W = (int) (fd * 0.03), lo = (int) (fd / 1000), hi = (int) (fd / 65);
        int frames = n / hop + 1;
        double[] f0 = new double[frames];
        double[] df = new double[hi + 2];
        for (int t = 0; t < frames; t++) {
            int c = (int) ((long) t * hop / D) - (W + hi) / 2;
            if (c < 0 || c + W + hi + 1 >= nd) continue;
            double e = 0;
            for (int j = 0; j < W; j++) e += d[c + j] * d[c + j];
            if (e / W < 1e-5) continue;                          // quieter than about −50 dBFS
            double run = 0;
            df[0] = 1;
            for (int tau = 1; tau <= hi; tau++) {
                double s = 0;
                for (int j = 0; j < W; j++) { double v = d[c + j] - d[c + j + tau]; s += v * v; }
                run += s;
                df[tau] = run > 0 ? s * tau / run : 1;
            }
            int best = -1;
            for (int tau = lo; tau < hi; tau++) {
                if (df[tau] < 0.15) { while (tau + 1 < hi && df[tau + 1] < df[tau]) tau++; best = tau; break; }
            }
            if (best < 0) continue;
            double a = df[best - 1], b = df[best], cc = df[best + 1], den = a - 2 * b + cc;
            double tb = best + (den != 0 ? 0.5 * (a - cc) / den : 0);
            f0[t] = fd / tb;
        }
        // 3-point median against single-frame octave slips
        double[] m = f0.clone();
        for (int t = 1; t + 1 < frames; t++) {
            double a = f0[t - 1], b = f0[t], c = f0[t + 1];
            if (a > 0 && b > 0 && c > 0) m[t] = Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
            else if (a == 0 && c == 0) m[t] = 0;
        }
        return m;
    }

    private int snap(double midi) {
        int base = (int) Math.round(midi), bestN = base;
        double bestD = 1e9;
        for (int k = base - 6; k <= base + 6; k++) if (allowed[((k % 12) + 12) % 12] && Math.abs(k - midi) < bestD) { bestD = Math.abs(k - midi); bestN = k; }
        return bestN;
    }

    /* ---------- TD-PSOLA ---------- */

    private float[][] process(float[][] x, int n) {
        float[] mono = new float[n];
        for (int c = 0; c < ch; c++) for (int i = 0; i < n; i++) mono[i] += x[c][i] / ch;
        double[] f0 = track(mono, n, fs, hop);
        int frames = f0.length;
        // correction ratio per hop
        double[] ratio = new double[frames];
        double a = tau > 0 ? 1 - Math.exp(-HOP_S / tau) : 1;
        boolean was = false;
        for (int t = 0; t < frames; t++) {
            ratio[t] = 1;
            if (f0[t] <= 0) { was = false; continue; }
            double midi = 69 + 12 * Math.log(f0[t] / 440) / Math.log(2);
            int s = snap(midi);
            if (!was || note < 0) note = s; else note += a * (s - note);
            was = true;
            double corr = (note - midi) * amount;
            ratio[t] = Math.pow(2, corr / 12);
            if (Math.abs(corr) > 0.05) corrected += HOP_S;
        }
        // analysis marks: pitch-synchronous in voiced parts, every hop elsewhere
        int cap = n / 40 + 16;
        int[] mk = new int[cap], per = new int[cap];
        boolean[] vo = new boolean[cap];
        int nm = 0, pos = 0;
        boolean prevV = false;
        while (pos < n) {
            int t = Math.min(frames - 1, pos / hop);
            boolean v = f0[t] > 0;
            int P = v ? (int) Math.round(fs / f0[t]) : hop;
            int m = pos;
            if (v && !prevV) {   // a voiced run starts on a waveform peak, then the marks follow the period
                int lo = Math.max(0, pos - P / 2), hi = Math.min(n - 1, pos + P / 2);
                float best = -2;
                for (int i = lo; i <= hi; i++) if (mono[i] > best) { best = mono[i]; m = i; }
                if (nm > 0 && m <= mk[nm - 1]) m = pos;
            }
            if (nm == cap) { cap *= 2; mk = java.util.Arrays.copyOf(mk, cap); per = java.util.Arrays.copyOf(per, cap); vo = java.util.Arrays.copyOf(vo, cap); }
            mk[nm] = m; per[nm] = P; vo[nm] = v; nm++;
            pos = m + P;
            prevV = v;
        }
        // synthesis: grains of two periods around the nearest analysis mark, placed at the corrected period
        float[][] y = new float[ch][n];
        float[] ws = new float[n];
        double ts = nm > 0 ? mk[0] : n;
        int k = 0;
        while (ts < n) {
            while (k + 1 < nm && Math.abs(mk[k + 1] - ts) < Math.abs(mk[k] - ts)) k++;
            int P = per[k], c0 = mk[k], at = (int) Math.round(ts);
            for (int i = -P; i < P; i++) {
                int src = c0 + i, dst = at + i;
                if (src < 0 || src >= n || dst < 0 || dst >= n) continue;
                float w = (float) (0.5 - 0.5 * Math.cos(Math.PI * (i + P) / P));
                for (int c = 0; c < ch; c++) y[c][dst] += x[c][src] * w;
                ws[dst] += w;
            }
            int t = Math.min(frames - 1, Math.max(0, at / hop));
            ts += vo[k] ? P / ratio[t] : P;
        }
        for (int i = 0; i < n; i++) {
            float w = ws[i];
            for (int c = 0; c < ch; c++) y[c][i] = w < 0.05f ? x[c][i] : y[c][i] / Math.max(w, 0.5f);
        }
        return y;
    }

    /* ---------- key finding: which major or minor key fits the sung notes best (Krumhansl–Schmuckler) ---------- */

    public static final class KeyFinder extends Node {
        final int ch, fs, hop;
        final double[] hist = new double[12];
        float[] mono = new float[1 << 18];
        int len;

        public KeyFinder(int ch, int fs) { this.ch = ch; this.fs = fs; hop = (int) Math.round(fs * 0.01); }

        @Override public void push(float[][] b, int n) {
            if (len + n > mono.length) flush(false);
            if (len + n > mono.length) mono = java.util.Arrays.copyOf(mono, len + n);
            for (int i = 0; i < n; i++) { float s = 0; for (int c = 0; c < ch; c++) s += b[c][i]; mono[len + i] = s / ch; }
            len += n;
        }

        private void flush(boolean all) {
            if (len < fs / 2) { if (all) len = 0; return; }
            double[] f0 = track(mono, len, fs, hop);
            for (double f : f0) if (f > 0) {
                double midi = 69 + 12 * Math.log(f / 440) / Math.log(2);
                int pc = (((int) Math.round(midi)) % 12 + 12) % 12;
                hist[pc] += 1 - 2 * Math.abs(midi - Math.round(midi));   // notes sung in tune count more
            }
            int keep = all ? 0 : Math.min(len, fs / 10);
            System.arraycopy(mono, len - keep, mono, 0, keep);
            len = keep;
        }

        @Override public void end() { flush(true); }

        static final double[] MAJ = { 6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88 };
        static final double[] MIN = { 6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17 };

        /** {key 0..11, 1 = minor} or null when almost nothing was sung */
        public int[] key() {
            double tot = 0;
            for (double h : hist) tot += h;
            if (tot < 20) return null;
            double best = -2; int bk = 0, bm = 0;
            for (int m = 0; m < 2; m++) for (int k = 0; k < 12; k++) {
                double[] p = m == 0 ? MAJ : MIN;
                double r = corr(hist, p, k);
                if (r > best) { best = r; bk = k; bm = m; }
            }
            return new int[] { bk, bm };
        }

        private static double corr(double[] h, double[] p, int k) {
            double mh = 0, mp = 0;
            for (int i = 0; i < 12; i++) { mh += h[i]; mp += p[i]; }
            mh /= 12; mp /= 12;
            double s = 0, sh = 0, sp = 0;
            for (int i = 0; i < 12; i++) { double a = h[(i + k) % 12] - mh, b = p[i] - mp; s += a * b; sh += a * a; sp += b * b; }
            return s / Math.sqrt(sh * sp + 1e-12);
        }
    }
}
