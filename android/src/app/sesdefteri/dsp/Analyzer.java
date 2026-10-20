package app.sesdefteri.dsp;

/**
 * First pass over a recording: measures everything the automatic decisions need, in one go and with
 * bounded memory (statistics are kept per 1 dB loudness bucket, not per frame).
 * For stereo input it measures four "views" (left, right, mid, side) so the channel decision can be
 * made afterwards and the rest of the measurements taken from the view that will actually be kept.
 */
public final class Analyzer extends Node {
    public static final int NB = 100;                // loudness buckets, 1 dB each, -100..0 dBFS
    public static final int SIB = 48;                // sibilance ratio buckets, 1 dB each, -48..0 dB
    public static final double[] HUM = { 50, 100, 60, 120 };

    final int inCh, fs, N, hop, bins, views;
    final Fft fft;
    final double[] win;
    final float[][] buf;                             // per view, last N samples
    int fill;
    long frames, samples;
    final double[] re, im;

    // per view statistics
    final long[][] count;                            // [view][bucket]
    final double[][][] spec;                         // [view][bucket][bin]
    final long[][][] sib;                            // [view][bucket][sib bucket]
    final FloatList[] level;                         // [view] frame level in dB (time order)
    final FloatList onset = new FloatList();         // spectral flux of the main view
    final double[] prevLogMag, bandE;
    final int[] bandOf;
    // hum: long-window spectrum (fine frequency resolution) of the main view, low bins only, per loudness bucket
    final int LN, humBins;
    final Fft lfft;
    final double[] lwin, lre, lim;
    final float[] lbuf;
    int lfill;
    final double[][] humSpec;
    final long[] humCount;
    final Loudness loud;
    // channel statistics (stereo)
    double sLL, sRR, sLR;
    long clipL, clipR;
    double peak;

    public Analyzer(int channels, int fs) {
        inCh = channels;
        this.fs = fs;
        views = channels == 2 ? 4 : 1;
        N = Fft.nextPow2((int) Math.round(fs * 0.021));
        hop = N / 2;
        bins = N / 2 + 1;
        fft = new Fft(N);
        win = Fft.hann(N, false);
        buf = new float[views][N];
        re = new double[N];
        im = new double[N];
        count = new long[views][NB];
        spec = new double[views][NB][bins];
        sib = new long[views][NB][SIB];
        level = new FloatList[views];
        for (int v = 0; v < views; v++) level[v] = new FloatList();
        prevLogMag = new double[8];
        java.util.Arrays.fill(prevLogMag, -80);
        bandE = new double[8];
        bandOf = new int[bins];
        double[] edges = { 30, 120, 250, 500, 1000, 2000, 4000, 8000, 16000 };
        for (int k = 0; k < bins; k++) {
            double f = (double) k * fs / N;
            bandOf[k] = -1;
            for (int q = 0; q < 8; q++) if (f >= edges[q] && f < edges[q + 1]) bandOf[k] = q;
        }
        LN = Fft.nextPow2((int) Math.round(fs * 0.30));
        humBins = (int) (420.0 * LN / fs) + 1;
        lfft = new Fft(LN);
        lwin = Fft.hann(LN, false);
        lre = new double[LN];
        lim = new double[LN];
        lbuf = new float[LN];
        humSpec = new double[NB][humBins];
        humCount = new long[NB];
        loud = new Loudness(1, fs);
    }

    private final float[][] one = new float[1][1];

    @Override public void push(float[][] b, int n) {
        for (int i = 0; i < n; i++) {
            float l = b[0][i];
            if (views == 4) {
                float r = b[1][i];
                buf[0][fill] = l;
                buf[1][fill] = r;
                buf[2][fill] = 0.5f * (l + r);
                buf[3][fill] = 0.5f * (l - r);
                sLL += l * l; sRR += r * r; sLR += l * r;
                if (Math.abs(l) >= 0.999f) clipL++;
                if (Math.abs(r) >= 0.999f) clipR++;
                peak = Math.max(peak, Math.max(Math.abs(l), Math.abs(r)));
            } else {
                float m = l;
                if (inCh > 1) { float s = 0; for (int c = 0; c < inCh; c++) s += b[c][i]; m = s / inCh; }
                buf[0][fill] = m;
                peak = Math.max(peak, Math.abs(m));
                if (Math.abs(m) >= 0.999f) clipL++;
            }
            float main = buf[views == 4 ? 2 : 0][fill];
            lbuf[lfill++] = main;
            if (lfill == LN) longFrame();
            one[0][0] = main;
            loud.measure(one, 1);
            fill++;
            if (fill == N) frame();
        }
        samples += n;
    }

    private void frame() {
        int mainView = views == 4 ? 2 : 0;
        int bucketMain = 0;
        for (int v = 0; v < views; v++) {
            float[] x = buf[v];
            double e = 0;
            for (int i = 0; i < N; i++) { double w = x[i] * win[i]; re[i] = w; im[i] = 0; e += w * w; }
            double lvl = 10 * Math.log10(e / (N * 0.375) + 1e-15);       // Hann power correction
            level[v].add((float) lvl);
            int bk = Math.max(0, Math.min(NB - 1, (int) Math.floor(lvl + 100)));
            count[v][bk]++;
            fft.transform(re, im, false);
            double[] sp = spec[v][bk];
            double sTot = 0, sHi = 0;
            int k100 = (int) Math.ceil(100.0 * N / fs), k5 = (int) Math.round(5000.0 * N / fs), k10 = Math.min(bins - 1, (int) Math.round(10000.0 * N / fs));
            double flux = 0;
            if (v == mainView) java.util.Arrays.fill(bandE, 0);
            for (int k = 0; k < bins; k++) {
                double p = re[k] * re[k] + im[k] * im[k];
                sp[k] += p;
                if (k >= k100 && k <= k10) { sTot += p; if (k >= k5) sHi += p; }
                if (v == mainView && bandOf[k] >= 0) bandE[bandOf[k]] += p;
            }
            if (v == mainView) {
                // onset strength: rise of the level in each octave band (dB), all bands weighted equally,
                // so a bass drum counts as much as a snare
                for (int q = 0; q < bandE.length; q++) {
                    double l = Math.max(-80, 10 * Math.log10(bandE[q] / (N * 0.375 * N / 2.0) + 1e-12));
                    double d = l - prevLogMag[q];
                    if (d > 0) flux += d;
                    prevLogMag[q] = l;
                }
            }
            double r = sTot > 0 ? 10 * Math.log10(sHi / sTot + 1e-12) : -99;
            int sb = Math.max(0, Math.min(SIB - 1, (int) Math.floor(r + SIB)));
            sib[v][bk][sb]++;
            if (v == mainView) { onset.add((float) flux); bucketMain = bk; }
        }
        for (float[] x : buf) System.arraycopy(x, hop, x, 0, N - hop);
        fill = N - hop;
        frames++;
    }

    private void longFrame() {
        double e = 0;
        for (int i = 0; i < LN; i++) { double w = lbuf[i] * lwin[i]; lre[i] = w; lim[i] = 0; e += w * w; }
        double lvl = 10 * Math.log10(e / (LN * 0.375) + 1e-15);
        int bk = Math.max(0, Math.min(NB - 1, (int) Math.floor(lvl + 100)));
        lfft.transform(lre, lim, false);
        for (int k = 0; k < humBins; k++) humSpec[bk][k] += lre[k] * lre[k] + lim[k] * lim[k];
        humCount[bk]++;
        lfill = 0;
    }

    @Override public void end() { }

    public Analysis finish() {
        Analysis a = new Analysis();
        a.fs = fs; a.inCh = inCh; a.samples = samples; a.frameHop = hop; a.fftSize = N;
        a.peak = peak;
        // ---- channels ----
        int view = 0;
        a.channelMode = Stages.ChannelMap.KEEP;
        if (views == 4) {
            double lDb = 10 * Math.log10(sLL / Math.max(1, samples) + 1e-15), rDb = 10 * Math.log10(sRR / Math.max(1, samples) + 1e-15);
            double corr = sLR / Math.sqrt(sLL * sRR + 1e-30);
            a.leftDb = lDb; a.rightDb = rDb; a.corr = corr;
            double flatL = flatness(0), flatR = flatness(1);
            a.flatL = flatL; a.flatR = flatR;
            double clipFL = (double) clipL / Math.max(1, samples), clipFR = (double) clipR / Math.max(1, samples);
            if (lDb < -60 && rDb > -50) { a.channelMode = Stages.ChannelMap.RIGHT; a.channelReason = "left-silent"; view = 1; }
            else if (rDb < -60 && lDb > -50) { a.channelMode = Stages.ChannelMap.LEFT; a.channelReason = "right-silent"; view = 0; }
            else if (corr > 0.98 && Math.abs(lDb - rDb) < 1.0) { a.channelMode = Stages.ChannelMap.MONO; a.channelReason = "identical"; view = 2; }
            else if (corr < -0.95 && Math.abs(lDb - rDb) < 1.0) { a.channelMode = Stages.ChannelMap.DIFF; a.channelReason = "inverted"; view = 3; }
            else if (Math.abs(corr) < 0.2 && (clipFL > 0.01 || flatL > 0.5) && flatR < 0.35 && clipFR < 0.001) { a.channelMode = Stages.ChannelMap.RIGHT; a.channelReason = "left-broken"; view = 1; }
            else if (Math.abs(corr) < 0.2 && (clipFR > 0.01 || flatR > 0.5) && flatL < 0.35 && clipFL < 0.001) { a.channelMode = Stages.ChannelMap.LEFT; a.channelReason = "right-broken"; view = 0; }
            else { view = 2; }
        }
        a.view = view;
        // ---- levels ----
        long[] cnt = count[view];
        long total = 0, nonSilent = 0;
        for (int i = 0; i < NB; i++) { total += cnt[i]; if (i > 0) nonSilent += cnt[i]; }
        a.noiseDb = percentileDb(cnt, 0.10, true);
        a.speechDb = percentileDb(cnt, 0.95, true);
        a.snr = a.speechDb - a.noiseDb;
        // noise spectrum: frames between the 2nd and 15th percentile (non-silent)
        a.noisePower = new double[bins];
        long lo = Math.round(0.02 * nonSilent), hi = Math.max(lo + 1, Math.round(0.15 * nonSilent)), acc = 0, used = 0;
        for (int b = 1; b < NB; b++) {
            long c0 = acc, c1 = acc + cnt[b];
            acc = c1;
            if (c1 <= lo || c0 >= hi || cnt[b] == 0) continue;
            for (int k = 0; k < bins; k++) a.noisePower[k] += spec[view][b][k];
            used += cnt[b];
        }
        if (used > 0) for (int k = 0; k < bins; k++) a.noisePower[k] /= used;
        // speech frames: well above the noise and within 30 dB of the loud end
        double thr = Math.max(a.noiseDb + Math.max(6, 0.35 * a.snr), a.speechDb - 30);
        a.speechThrDb = thr;
        int tb = Math.max(1, (int) Math.floor(thr + 100));
        a.ltasPower = new double[bins];
        long sp = 0;
        double sumL = 0, sumL2 = 0;
        long[] sibAll = new long[SIB];
        for (int b = tb; b < NB; b++) {
            if (cnt[b] == 0) continue;
            for (int k = 0; k < bins; k++) a.ltasPower[k] += spec[view][b][k];
            for (int s = 0; s < SIB; s++) sibAll[s] += sib[view][b][s];
            sp += cnt[b];
            double l = b - 100 + 0.5;
            sumL += cnt[b] * l; sumL2 += cnt[b] * l * l;
        }
        a.speechFrames = sp;
        if (sp > 0) {
            for (int k = 0; k < bins; k++) a.ltasPower[k] /= sp;
            double mean = sumL / sp;
            a.speechMeanDb = mean;
            a.speechStdDb = Math.sqrt(Math.max(0, sumL2 / sp - mean * mean));
        }
        a.sibP98 = percentileOf(sibAll, 0.98) - SIB;
        a.sibP50 = percentileOf(sibAll, 0.50) - SIB;
        // hum: in the quietest long blocks, a narrow peak at 50/60 Hz (or its 2nd harmonic) well above its neighbours
        a.humRatioDb = new double[HUM.length];
        {
            long totL = 0;
            for (int bb = 1; bb < NB; bb++) totL += humCount[bb];
            long lim2 = Math.max(1, Math.round(0.3 * totL)), accL = 0;
            double[] hs = new double[humBins];
            for (int bb = 1; bb < NB && accL < lim2; bb++) {
                if (humCount[bb] == 0) continue;
                accL += humCount[bb];
                for (int k = 0; k < humBins; k++) hs[k] += humSpec[bb][k];
            }
            for (int h = 0; h < HUM.length; h++) {
                int k = (int) Math.round(HUM[h] * LN / fs);
                double pk = Math.max(hs[k], Math.max(hs[k - 1], hs[k + 1]));
                // baseline: geometric mean of symmetric neighbours (follows a 1/f slope)
                double lg = 0; int q = 0;
                for (int d = 4; d <= 7; d++) { lg += Math.log(hs[k - d] + 1e-30) + Math.log(hs[k + d] + 1e-30); q += 2; }
                a.humRatioDb[h] = 10 * Math.log10((pk + 1e-30) / Math.exp(lg / q));
            }
        }
        a.lra = loud.range();
        a.inLufs = loud.integrated();
        // sibilance: how loud are the "s" frames compared with the loud end of speech
        {
            long[] lv = new long[NB];
            long tot = 0;
            for (int bb = 1; bb < NB; bb++) for (int sI = SIB - 3; sI < SIB; sI++) { lv[bb] += sib[view][bb][sI]; tot += sib[view][bb][sI]; }
            a.sFrames = tot;
            a.sLevel90 = tot == 0 ? -100 : percentileDb(lv, 0.90, true);
        }
        // trim points from the level curve
        FloatList lv = level[view];
        double act = Math.max(a.noiseDb + Math.max(8, 0.4 * a.snr), -60);
        int first = -1, last = -1, run = 0;
        for (int i = 0; i < lv.n; i++) {
            if (lv.a[i] > act) { run++; if (run >= 5 && first < 0) first = i - 4; } else run = 0;
        }
        run = 0;
        for (int i = lv.n - 1; i >= 0; i--) {
            if (lv.a[i] > act) { run++; if (run >= 5) { last = i + 4; break; } } else run = 0;
        }
        a.activeStart = first < 0 ? 0 : (long) first * hop;
        a.activeEnd = last < 0 ? samples : Math.min(samples, (long) last * hop + N);
        a.onset = onset.toArray();
        a.onsetRate = (double) fs / hop;
        return a;
    }

    private double flatness(int v) {
        double lg = 0, ar = 0; int n = 0;
        int k0 = (int) Math.ceil(200.0 * N / fs), k1 = Math.min(bins - 1, (int) (8000.0 * N / fs));
        double[] s = new double[bins];
        for (int b = 0; b < NB; b++) for (int k = 0; k < bins; k++) s[k] += spec[v][b][k];
        for (int k = k0; k <= k1; k++) { lg += Math.log(s[k] + 1e-30); ar += s[k]; n++; }
        return Math.exp(lg / n) / (ar / n + 1e-30);
    }

    private static double percentileDb(long[] cnt, double q, boolean skipSilent) {
        long tot = 0;
        for (int i = skipSilent ? 1 : 0; i < cnt.length; i++) tot += cnt[i];
        if (tot == 0) return -100;
        long want = Math.round(q * (tot - 1)), acc = 0;
        for (int i = skipSilent ? 1 : 0; i < cnt.length; i++) { acc += cnt[i]; if (acc > want) return i - 100 + 0.5; }
        return 0;
    }

    private static double percentileOf(long[] h, double q) {
        long tot = 0;
        for (long v : h) tot += v;
        if (tot == 0) return 0;
        long want = Math.round(q * (tot - 1)), acc = 0;
        for (int i = 0; i < h.length; i++) { acc += h[i]; if (acc > want) return i + 0.5; }
        return h.length;
    }

    public static final class FloatList {
        public float[] a = new float[1024];
        public int n;
        void add(float v) { if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2); a[n++] = v; }
        float[] toArray() { return java.util.Arrays.copyOf(a, n); }
    }

    /** What the first pass found. */
    public static final class Analysis {
        public int fs, inCh, view, channelMode, frameHop, fftSize;
        public String channelReason = "";
        public long samples, activeStart, activeEnd, speechFrames;
        public double peak, leftDb, rightDb, corr, flatL, flatR;
        public double lra, inLufs, sLevel90;
        public long sFrames;
        public double noiseDb, speechDb, snr, speechThrDb, speechMeanDb, speechStdDb, sibP98, sibP50;
        public double[] noisePower, ltasPower, humRatioDb;
        public float[] onset;
        public double onsetRate;
    }
}
