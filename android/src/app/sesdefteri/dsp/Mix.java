package app.sesdefteri.dsp;

/**
 * Background music under a voice recording with automatic ducking: the music is turned down a little before
 * the voice starts and comes back up after a pause, like the envelope one draws by hand in Audacity.
 */
public final class Mix extends Node {
    /** endless music source (loops with a crossfade) */
    public interface Bed { void read(float[][] dst, int n) throws Exception; }

    final int vCh, outCh, fs, D;              // D = look-ahead in samples
    final double thr2, atk, rel, duck, bedGain;
    final int holdLen;
    final float[][] vDelay;                   // delayed voice (ring)
    final Bed bed;
    float[][] mbuf = new float[1][0], out = new float[1][0];
    long n0;                                  // samples received
    long total = -1, emitted;
    double env2, g = 1;
    int hold;
    int[] act;                                // voice activity ring (1 = speech) for the look-ahead window
    int actSum;
    final long fadeIn;
    long fadeOutStart = -1, fadeOutLen;
    // for tests / the report: music gain (dB) every 10 ms
    public final Analyzer.FloatList gainTrace = new Analyzer.FloatList();
    final int traceStep;

    /**
     * @param voiceSpeechDb 95th percentile frame level of the voice (from {@link Analyzer}) - sets the activity threshold
     * @param bedGainDb gain applied to the music so it sits at the chosen level between sentences
     * @param duckDb extra reduction while the voice is speaking (negative)
     */
    public Mix(int voiceChannels, int musicChannels, int fs, double voiceNoiseDb, double voiceSpeechDb, Bed bed, double bedGainDb, double duckDb, long voiceSamples) {
        vCh = voiceChannels;
        outCh = Math.max(voiceChannels, musicChannels);
        this.fs = fs;
        this.bed = bed;
        D = (int) Math.round(0.25 * fs);
        double thrDb = Math.max(voiceNoiseDb + 10, voiceSpeechDb - 28);
        thr2 = Math.pow(10, thrDb / 10);
        atk = 1 - Math.exp(-1 / (0.10 * fs));
        rel = 1 - Math.exp(-1 / (0.45 * fs));
        duck = Math.pow(10, duckDb / 20);
        bedGain = Math.pow(10, bedGainDb / 20);
        holdLen = (int) Math.round(0.6 * fs);
        vDelay = new float[vCh][D];
        act = new int[D + 1];
        fadeIn = Math.round(1.5 * fs);
        fadeOutLen = Math.round(Math.min(3.0 * fs, voiceSamples / 4));
        fadeOutStart = voiceSamples - fadeOutLen;
        traceStep = fs / 100;
    }

    @Override public void push(float[][] v, int n) throws Exception { process(v, n, false); }

    private void process(float[][] v, int n, boolean zeros) throws Exception {
        if (mbuf.length != outCh || mbuf[0].length < n) mbuf = new float[outCh][n];
        if (out.length != outCh || out[0].length < n) out = new float[outCh][n];
        if (gTmp.length < n) gTmp = new double[n];
        double ea = 1 - Math.exp(-1 / (0.02 * fs));
        int k = 0;
        for (int i = 0; i < n; i++) {
            long t = n0 + i;              // input index
            long o = t - D;               // output index
            // voice activity on the incoming (not yet delayed) voice
            double p = 0;
            if (!zeros) for (int c = 0; c < vCh; c++) p += v[c][i] * v[c][i];
            env2 += (p / vCh - env2) * ea;
            int a = env2 > thr2 ? 1 : 0;
            int ri = (int) (t % (D + 1));
            actSum += a - act[ri];
            act[ri] = a;
            // speech anywhere in [o, t] → duck (look-ahead), then hold
            if (actSum > 0) hold = holdLen;
            double want = hold > 0 ? duck : 1;
            if (hold > 0) hold--;
            g += (want - g) * (want < g ? atk : rel);
            // write voice into delay line, read the delayed sample
            int di = (int) (t % D);
            for (int c = 0; c < vCh; c++) {
                float in = zeros ? 0f : v[c][i];
                float old = vDelay[c][di];
                vDelay[c][di] = in;
                if (o >= 0 && (total < 0 || o < total)) out[c % outCh][k] = old;
            }
            if (o >= 0 && (total < 0 || o < total)) {
                gTmp[k] = g;
                k++;
                if (o % traceStep == 0) gainTrace.add((float) (20 * Math.log10(g)));
            }
        }
        if (k > 0) {
            bed.read(mbuf, k);
            for (int i = 0; i < k; i++) {
                long o = emitted + i;
                double f = 1;
                if (o < fadeIn) f = (double) o / fadeIn;
                if (o >= fadeOutStart) f *= Math.max(0, 1 - (double) (o - fadeOutStart) / fadeOutLen);
                double gm = gTmp[i] * bedGain * f;
                if (vCh == 1 && outCh == 2) out[1][i] = out[0][i];
                for (int c = 0; c < outCh; c++) out[c][i] += (float) (mbuf[c][i] * gm);
            }
            emitted += k;
            next.push(out, k);
        }
        n0 += n;
    }

    private double[] gTmp = new double[8192];

    @Override public void end() throws Exception {
        total = n0;
        int left = D + 1;
        float[][] z = new float[vCh][Math.min(4096, left)];
        while (emitted < total) {
            int n = (int) Math.min(z[0].length, total - emitted + 1);
            process(z, n, true);
        }
        next.end();
    }

    /** music from a 24-bit temp file, looped with a 2 s crossfade, any channel count mapped to outCh */
    public static final class FileBed implements Bed {
        final Pipeline.FileTemp src;
        final int ch, outCh, xf;
        final long frames;
        java.io.RandomAccessFile raf;
        long pos;
        byte[] rb = new byte[0];
        float[][] tmpA = new float[1][0], tmpB = new float[1][0];

        public FileBed(Pipeline.FileTemp src, int outCh, int fs) throws java.io.IOException {
            this.src = src;
            ch = src.ch;
            this.outCh = outCh;
            frames = src.frames;
            xf = (int) Math.min(2L * fs, frames / 4);
            raf = new java.io.RandomAccessFile(src.f, "r");
        }

        private void readAt(long p, float[][] dst, int off, int n) throws java.io.IOException {
            int need = n * ch * 3;
            if (rb.length < need) rb = new byte[need];
            raf.seek(p * ch * 3);
            raf.readFully(rb, 0, need);
            int q = 0;
            for (int i = 0; i < n; i++) {
                float sum = 0;
                for (int c = 0; c < ch; c++) {
                    int s = (rb[q] & 0xff) | ((rb[q + 1] & 0xff) << 8) | (rb[q + 2] << 16);
                    q += 3;
                    float v = s / 8388607f / Pipeline.FileTemp.SCALE;
                    if (c < outCh) dst[c][off + i] = v;
                    sum += v;
                }
                if (ch == 1) for (int c = 1; c < outCh; c++) dst[c][off + i] = dst[0][off + i];
                else if (outCh == 1) dst[0][off + i] = sum / ch;
            }
        }

        @Override public void read(float[][] dst, int n) throws Exception {
            int done = 0;
            long loopEnd = frames - xf;                 // play [0, loopEnd), crossfading the last xf into the start
            while (done < n) {
                if (frames <= 0) { for (float[] d : dst) java.util.Arrays.fill(d, done, n, 0f); return; }
                int take = (int) Math.min(n - done, loopEnd - pos);
                readAt(pos, dst, done, take);
                // crossfade region: the first xf samples of the loop also hear the tail
                if (pos < xf && everLooped) {
                    int m = (int) Math.min(take, xf - pos);
                    if (tmpA.length != outCh || tmpA[0].length < m) tmpA = new float[outCh][m];
                    readAt(loopEnd + pos, tmpA, 0, m);
                    for (int i = 0; i < m; i++) {
                        double w = (double) (pos + i) / xf;
                        for (int c = 0; c < outCh; c++) dst[c][done + i] = (float) (dst[c][done + i] * Math.sqrt(w) + tmpA[c][i] * Math.sqrt(1 - w));
                    }
                }
                pos += take;
                done += take;
                if (pos >= loopEnd) { pos = 0; everLooped = true; }
            }
        }

        boolean everLooped;

        public void close() { try { raf.close(); } catch (Exception ignored) { } }
    }
}
