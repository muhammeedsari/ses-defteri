package app.sesdefteri.dsp;

/**
 * STFT noise reduction + static EQ curve in one pass (75 % overlap, sqrt-Hann analysis/synthesis).
 * Noise reduction is a decision-directed Wiener filter driven by a measured noise profile,
 * with a gain floor so the remaining noise stays natural instead of "musical".
 * Output is sample-aligned with the input (the STFT latency is removed).
 */
public final class Spectral extends Node {
    final int ch, N, hop, bins, P;
    final Fft fft;
    final double[] win;
    final float[][] in;          // per channel: last N input samples (padded coordinates)
    final double[][] ola;        // overlap-add accumulator, length N
    int fill;                    // samples in current hop
    long received, emitted, total = -1;
    final double[] re, im;
    // noise reduction
    double[] noise;              // noise power per bin (same scaling as |X|^2 of this STFT); null = off
    double floor = 1, overSub = 1;
    public double alpha = 0.96;
    // 2.4: noise that changes over time (traffic, a fan switching speed) is followed with minimum statistics
    boolean track;
    double[][] smP, curMin; double[][][] subMin;
    int subLen, subPos, subIdx; static final int SUBS = 8;
    final double[][] prevG2, prevGamma;
    // equaliser (linear gain per bin); null = flat
    double[] eq;
    // late-reverb suppression (Lebart): reverb power ≈ decay · smoothed power 50 ms ago
    double[][][] past;             // [ch][slot][bin]
    double[][] ps;                 // smoothed power per channel
    double revDecay, revBeta, revFloor;
    int revD, revSlot;
    final double[] g, gs;
    float[][] out;
    int outN;
    public double meanGain = 0; long frames = 0;

    public Spectral(int channels, int fs) {
        ch = channels;
        N = Fft.nextPow2((int) Math.round(fs * 0.021));
        hop = N / 4;
        bins = N / 2 + 1;
        P = N - hop;
        fft = new Fft(N);
        win = Fft.hann(N, true);
        in = new float[ch][N];
        ola = new double[ch][N];
        re = new double[N];
        im = new double[N];
        prevG2 = new double[ch][bins];
        prevGamma = new double[ch][bins];
        for (double[] a : prevG2) java.util.Arrays.fill(a, 1);
        g = new double[bins];
        gs = new double[bins];
        out = new float[ch][hop];
        fill = P;                  // start with P zeros already "received" in padded coordinates
    }

    /** noise power spectrum measured with a Hann window of length N (from Analyzer); reductionDb = gain floor */
    public Spectral noise(double[] hannPower, double reductionDb, double overSubtract) {
        // Analyzer uses a plain Hann window; this STFT uses sqrt-Hann. Scale by the window energy ratio.
        double eH = 0, eS = 0;
        double[] h = Fft.hann(N, false);
        for (int i = 0; i < N; i++) { eH += h[i] * h[i]; eS += win[i] * win[i]; }
        noise = new double[bins];
        for (int k = 0; k < bins; k++) noise[k] = hannPower[k] * eS / eH;
        floor = Math.pow(10, -reductionDb / 20);
        overSub = overSubtract;
        return this;
    }

    /** follow changing noise: per-bin minimum of the smoothed power over the last ~2.5 s (never below the profile) */
    public Spectral tracking(int fs) {
        track = true;
        smP = new double[ch][bins]; curMin = new double[ch][bins]; subMin = new double[ch][SUBS][bins];
        for (int c = 0; c < ch; c++) { java.util.Arrays.fill(curMin[c], Double.MAX_VALUE); for (double[] a : subMin[c]) java.util.Arrays.fill(a, Double.MAX_VALUE); }
        subLen = Math.max(4, (int) Math.round(2.5 * fs / hop / SUBS));
        return this;
    }

    /** late-reverb suppression for a room with the given T60 (s); strength 0.5–1.5 */
    public Spectral dereverb(double fs, double t60, double strength) {
        double hopSec = hop / fs, td = 0.05;
        revD = Math.max(2, (int) Math.round(td / hopSec));
        revDecay = Math.exp(-2 * 3 * Math.log(10) / t60 * revD * hopSec);
        revBeta = 1 + 1.5 * strength;
        revFloor = Math.pow(10, -(10 + 8 * strength) / 20);
        past = new double[ch][revD][bins];
        ps = new double[ch][bins];
        return this;
    }

    /** per-bin linear gains */
    public Spectral eq(double[] perBinGain) { eq = perBinGain; return this; }

    public int fftSize() { return N; }

    @Override public void push(float[][] b, int n) throws Exception {
        int off = 0;
        while (off < n) {
            int take = Math.min(n - off, N - fill);
            for (int c = 0; c < ch; c++) System.arraycopy(b[c], off, in[c], fill, take);
            fill += take;
            off += take;
            received += take;
            if (fill == N) frame();
        }
    }

    private void frame() throws Exception {
        for (int c = 0; c < ch; c++) {
            float[] x = in[c];
            for (int i = 0; i < N; i++) { re[i] = x[i] * win[i]; im[i] = 0; }
            fft.transform(re, im, false);
            computeGains(c);
            for (int k = 0; k < bins; k++) {
                double gk = gs[k];
                re[k] *= gk; im[k] *= gk;
                if (k > 0 && k < N / 2) { re[N - k] = re[k]; im[N - k] = -im[k]; }
            }
            fft.transform(re, im, true);
            double[] o = ola[c];
            double sc = 0.5 / N;
            for (int i = 0; i < N; i++) o[i] += re[i] * win[i] * sc;
        }
        // first hop of the accumulator is final
        if (out[0].length < hop) out = new float[ch][hop];
        long startPadded = received + P - N;      // padded index of in[0]
        long firstOrig = startPadded - P;         // original index of the final hop's first sample
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

    private void computeGains(int c) {
        if (past != null) { reverbGains(c); return; }
        if (noise == null) {
            for (int k = 0; k < bins; k++) gs[k] = eq == null ? 1 : eq[k];
            return;
        }
        double[] pg2 = prevG2[c], pga = prevGamma[c];
        double[] sp = track ? smP[c] : null, cm = track ? curMin[c] : null;
        if (track && c == ch - 1 && ++subPos >= subLen) subPos = 0;
        for (int k = 0; k < bins; k++) {
            double p = re[k] * re[k] + im[k] * im[k];
            double nk = noise[k] * overSub + 1e-20;
            if (sp != null) {
                sp[k] = 0.85 * sp[k] + 0.15 * p;
                if (sp[k] < cm[k]) cm[k] = sp[k];
                double mn = cm[k];
                for (double[] sm : subMin[c]) if (sm[k] < mn) mn = sm[k];
                double tn = mn * 1.5 * overSub;
                if (mn < Double.MAX_VALUE / 2 && tn > nk) nk = tn;
            }
            double gamma = p / nk;
            double xi = alpha * pg2[k] * pga[k] + (1 - alpha) * Math.max(gamma - 1, 0);
            double gk = xi / (1 + xi);
            if (gk < floor) gk = floor;
            g[k] = gk;
            pga[k] = gamma;
        }
        // light smoothing across frequency reduces isolated "musical" bins
        double sum = 0;
        for (int k = 0; k < bins; k++) {
            double a = g[Math.max(0, k - 1)], b = g[k], d = g[Math.min(bins - 1, k + 1)];
            double v = 0.25 * a + 0.5 * b + 0.25 * d;
            if (v < floor) v = floor;
            pg2[k] = v * v;
            sum += v;
            gs[k] = eq == null ? v : v * eq[k];
        }
        if (track && subPos == 0 && c == ch - 1) {
            for (int q = 0; q < ch; q++) { double[] t = subMin[q][subIdx]; System.arraycopy(curMin[q], 0, t, 0, bins); java.util.Arrays.fill(curMin[q], Double.MAX_VALUE); }
            subIdx = (subIdx + 1) % SUBS;
        }
        meanGain += sum / bins; frames++;
    }

    private void reverbGains(int c) {
        double[] sm = ps[c], old = past[c][revSlot];
        for (int k = 0; k < bins; k++) {
            double p = re[k] * re[k] + im[k] * im[k];
            sm[k] = 0.6 * sm[k] + 0.4 * p;
            double late = revDecay * old[k];
            double gk = 1 - revBeta * late / (sm[k] + 1e-20);
            g[k] = gk < revFloor ? revFloor : gk;
            old[k] = sm[k];                        // this slot is read again revD frames later
        }
        if (c == ch - 1) revSlot = (revSlot + 1) % revD;
        double[] pg2 = prevG2[c];
        for (int k = 0; k < bins; k++) {
            double a = g[Math.max(0, k - 1)], b = g[k], d = g[Math.min(bins - 1, k + 1)];
            double v = 0.25 * a + 0.5 * b + 0.25 * d;
            v = 0.5 * Math.sqrt(pg2[k]) + 0.5 * v;   // smooth in time too
            pg2[k] = v * v;
            gs[k] = v;
        }
    }

    @Override public void end() throws Exception {
        total = received;
        // feed zeros until every original sample has been emitted
        float[][] z = new float[ch][hop];
        while (emitted < total) push(z, hop);
        if (next != null) next.end();
    }
}
