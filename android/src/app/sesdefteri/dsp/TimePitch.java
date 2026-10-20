package app.sesdefteri.dsp;

/**
 * Speed and pitch change. A phase vocoder with identity phase locking stretches time without changing pitch;
 * pitch is then moved by resampling. When the pitch moves, the spectral envelope (formants: what makes a voice
 * sound like that person) can be kept in place by re-shaping each frame with its cepstral envelope, so a voice
 * shifted up does not turn into a "chipmunk".
 */
public final class TimePitch extends Node {
    final int ch, N, Ha, Hs, bins, lifter, fs;
    final double pitch;              // frequency ratio
    final boolean formant;
    final Fft fft;
    final double[] win;
    final double[][] prevPhase, synPhase;
    final double[] re, im, mag, ph, env, cre, cim;
    float[][] in;                    // input history (padded coordinates)
    long in0;                        // padded index of in[*][0]
    int inLen;
    long nextFrame;                  // padded index where the next analysis frame starts
    int frameNo;
    final double[][] ola;
    final double[] wsum;
    long outBase;                    // padded output index of ola[0]
    long emitted, wanted = -1, received;
    float[][] out;
    final Resampler rs;
    final Node tail;
    boolean first = true;

    /** @param speed tempo factor (2 = twice as fast); @param semitones pitch change; @param keepFormants envelope correction */
    public TimePitch(int channels, int fs, double speed, double semitones, boolean keepFormants) {
        ch = channels;
        this.fs = fs;
        pitch = Math.pow(2, semitones / 12.0);
        formant = keepFormants && Math.abs(semitones) > 0.01;
        N = Fft.nextPow2((int) Math.round(fs * 0.043));
        Ha = N / 8;
        Hs = Math.max(1, (int) Math.round(Ha * pitch / speed));
        bins = N / 2 + 1;
        lifter = Math.max(20, (int) Math.round(fs / 400.0));
        fft = new Fft(N);
        win = Fft.hann(N, false);
        prevPhase = new double[ch][bins];
        synPhase = new double[ch][bins];
        re = new double[N]; im = new double[N]; mag = new double[bins]; ph = new double[bins]; env = new double[bins];
        cre = new double[N]; cim = new double[N];
        in = new float[ch][N * 4];
        inLen = N / 2;                         // N/2 zeros of padding so frame 0 is centred on sample 0
        ola = new double[ch][N * 2];
        wsum = new double[N * 2];
        out = new float[ch][N];
        if (Math.abs(pitch - 1) > 1e-6) {
            rs = new Resampler(ch, pitch, 24);
            tail = rs;
        } else { rs = null; tail = null; }
    }

    /** the node that receives the final audio is attached to the resampler when pitch changes */
    @Override public Node to(Node n) {
        if (rs != null) { rs.to(n); next = rs; return n; }
        next = n; return n;
    }

    @Override public void push(float[][] b, int n) throws Exception {
        if (inLen + n > in[0].length) {
            int cap = Math.max(inLen + n, in[0].length * 2);
            for (int c = 0; c < ch; c++) in[c] = java.util.Arrays.copyOf(in[c], cap);
        }
        for (int c = 0; c < ch; c++) System.arraycopy(b[c], 0, in[c], inLen, n);
        inLen += n;
        received += n;
        while (nextFrame + N <= in0 + inLen) frame();
        // drop consumed input
        int drop = (int) (nextFrame - in0);
        if (drop > N * 2) {
            for (int c = 0; c < ch; c++) System.arraycopy(in[c], drop, in[c], 0, inLen - drop);
            inLen -= drop;
            in0 += drop;
        }
    }

    private void frame() throws Exception {
        int off = (int) (nextFrame - in0);
        for (int c = 0; c < ch; c++) {
            float[] x = in[c];
            for (int i = 0; i < N; i++) { re[i] = x[off + i] * win[i]; im[i] = 0; }
            fft.transform(re, im, false);
            for (int k = 0; k < bins; k++) { mag[k] = Math.hypot(re[k], im[k]); ph[k] = Math.atan2(im[k], re[k]); }
            double[] pp = prevPhase[c], sp = synPhase[c];
            if (first) {
                for (int k = 0; k < bins; k++) sp[k] = ph[k];
            } else {
                // peaks get a phase advance from their instantaneous frequency; other bins are locked to their peak
                int lastPeak = -1;
                int[] peakOf = peakMap();
                for (int k = 0; k < bins; k++) {
                    int pk = peakOf[k];
                    if (pk == k) {
                        double omega = 2 * Math.PI * k / N;
                        double d = ph[k] - pp[k] - omega * Ha;
                        d -= 2 * Math.PI * Math.round(d / (2 * Math.PI));
                        sp[k] = sp[k] + (omega + d / Ha) * Hs;
                    }
                }
                for (int k = 0; k < bins; k++) {
                    int pk = peakOf[k];
                    if (pk != k) sp[k] = sp[pk] + (ph[k] - ph[pk]);
                }
            }
            System.arraycopy(ph, 0, pp, 0, bins);
            if (formant) envelopeCorrect();
            for (int k = 0; k < bins; k++) {
                re[k] = mag[k] * Math.cos(sp[k]);
                im[k] = mag[k] * Math.sin(sp[k]);
                if (k > 0 && k < N / 2) { re[N - k] = re[k]; im[N - k] = -im[k]; }
            }
            im[0] = 0; im[N / 2] = 0;
            fft.transform(re, im, true);
            double[] o = ola[c];
            int base = (int) ((long) frameNo * Hs - outBase);
            for (int i = 0; i < N; i++) {
                o[base + i] += re[i] / N * win[i];
                if (c == 0) wsum[base + i] += win[i] * win[i];
            }
        }
        first = false;
        frameNo++;
        nextFrame += Ha;
        // samples before frameNo*Hs are final
        long finalUpto = (long) frameNo * Hs;
        flushOut(finalUpto);
    }

    private int[] peakBuf;

    private int[] peakMap() {
        if (peakBuf == null) peakBuf = new int[bins];
        // find local maxima, then assign every bin to the nearest peak (by valley)
        int last = 0;
        java.util.ArrayList<Integer> peaks = new java.util.ArrayList<>();
        for (int k = 0; k < bins; k++) {
            double m = mag[k];
            boolean pk = (k < 2 || m > mag[k - 1] && m > mag[k - 2]) && (k > bins - 3 || m >= mag[k + 1] && m >= mag[k + 2]);
            if (pk) peaks.add(k);
        }
        if (peaks.isEmpty()) peaks.add(0);
        int pi = 0;
        for (int k = 0; k < bins; k++) {
            while (pi + 1 < peaks.size() && Math.abs(peaks.get(pi + 1) - k) < Math.abs(peaks.get(pi) - k)) pi++;
            peakBuf[k] = peaks.get(pi);
        }
        return peakBuf;
    }

    /** multiply magnitudes by E(k*pitch)/E(k) so that after resampling by "pitch" the envelope is where it was */
    private void envelopeCorrect() {
        for (int k = 0; k < N; k++) {
            int kk = k < bins ? k : N - k;
            cre[k] = Math.log(mag[kk] + 1e-9);
            cim[k] = 0;
        }
        fft.transform(cre, cim, true);
        // adaptive lifter: stay below the pitch period found as the cepstral peak, so harmonics never leak in
        int lf = lifter;
        int q0 = Math.max(2, fs / 500), q1 = Math.min(N / 2 - 1, fs / 70);
        double best = 0; int bq = -1;
        for (int q = q0; q <= q1; q++) if (cre[q] / N > best) { best = cre[q] / N; bq = q; }
        if (bq > 0 && best > 0.08) lf = Math.max(fs / 1000, Math.min(fs / 300, (int) (0.7 * bq)));
        for (int q = 0; q < N; q++) {
            int qq = q < N / 2 ? q : N - q;
            if (qq >= lf) { cre[q] = 0; cim[q] = 0; }
            else { cre[q] /= N; cim[q] /= N; }
        }
        fft.transform(cre, cim, false);
        for (int k = 0; k < bins; k++) env[k] = cre[k];
        for (int k = 0; k < bins; k++) {
            double src = k * pitch;
            double e;
            if (src >= bins - 1) e = env[bins - 1] - 6;          // beyond Nyquist: fade
            else { int i0 = (int) src; double t = src - i0; e = env[i0] + t * (env[i0 + 1] - env[i0]); }
            double d = e - env[k];
            if (d > 2.3) d = 2.3;                                 // at most +20 dB
            mag[k] *= Math.exp(d);
        }
    }

    private void flushOut(long upto) throws Exception {
        int cnt = (int) (upto - outBase);
        if (cnt <= 0) return;
        long padStart = outBase;
        // padded output index q ↔ original output index q - N/2*Hs/Ha (the padding is stretched too)
        long shift = Math.round((N / 2.0) * Hs / Ha);
        if (out[0].length < cnt) out = new float[ch][cnt];
        int k = 0;
        for (int i = 0; i < cnt; i++) {
            long orig = padStart + i - shift;
            if (orig < 0) continue;
            if (wanted >= 0 && emitted + k >= wanted) break;
            double w = wsum[i];
            for (int c = 0; c < ch; c++) out[c][k] = (float) (w > 1e-3 ? ola[c][i] / Math.max(w, 0.1) : 0);
            k++;
        }
        for (int c = 0; c < ch; c++) {
            System.arraycopy(ola[c], cnt, ola[c], 0, ola[c].length - cnt);
            java.util.Arrays.fill(ola[c], ola[c].length - cnt, ola[c].length, 0);
        }
        System.arraycopy(wsum, cnt, wsum, 0, wsum.length - cnt);
        java.util.Arrays.fill(wsum, wsum.length - cnt, wsum.length, 0);
        outBase += cnt;
        if (k > 0) { emitted += k; if (next != null) next.push(out, k); }
    }

    @Override public void end() throws Exception {
        wanted = Math.round(received * (double) Hs / Ha);
        float[][] z = new float[ch][Ha];
        int guard = 0;
        while (emitted < wanted && guard++ < 100000) push(z, Ha);
        if (next != null) next.end();
    }
}
