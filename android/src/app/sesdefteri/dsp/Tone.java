package app.sesdefteri.dsp;

/**
 * 4.2: bass and treble the way music apps do it, so the change is heard on a phone speaker too.
 * Bass: low shelf + a lift where small speakers still play (≈200 Hz) + "psychoacoustic bass": harmonics of the deep
 * notes (2nd/3rd, made level-independent with Chebyshev polynomials) that the ear hears as the missing low note.
 * Treble: high shelf + presence lift + a light exciter (harmonics of the 3 kHz+ band). Cuts (negative) are plain shelves.
 * A pre-gain gives the boost headroom, so the limiter doesn't flatten it.
 */
public final class Tone {
    private final Biquad bShelf, bPeak, bLp, bHp, bLp2, tShelf, tPeak, tHp, tHp2;
    private final float hb, ht, pre;
    private final double dec;
    private double eb = 1e-4, et = 1e-4;
    private float[] m = new float[0], h = new float[0];

    public Tone(double fs, double bass, double treble) {
        dec = Math.exp(-1 / (fs * 0.03));
        bShelf = bass != 0 ? new Biquad(2).setLowShelf(fs, 120, 0.7, bass) : null;
        bPeak = bass != 0 ? Biquad.peaking(2, fs, 220, 0.8, bass * 0.5) : null;
        boolean hbOn = bass > 0;
        bLp = hbOn ? Biquad.lowpass(1, fs, 140, 0.707) : null;
        bHp = hbOn ? Biquad.highpass(1, fs, 170, 0.707) : null;
        bLp2 = hbOn ? Biquad.lowpass(1, fs, 600, 0.707) : null;
        hb = hbOn ? (float) (bass / 18 * 2.2) : 0;
        tShelf = treble != 0 ? new Biquad(2).setHighShelf(fs, 4500, 0.7, treble) : null;
        tPeak = treble != 0 ? Biquad.peaking(2, fs, 2800, 0.9, treble * 0.35) : null;
        boolean htOn = treble > 0;
        tHp = htOn ? Biquad.highpass(1, fs, 3000, 0.707) : null;
        tHp2 = htOn ? Biquad.highpass(1, fs, 6000, 0.707) : null;
        ht = htOn ? (float) (treble / 18 * 0.6) : 0;
        pre = (float) Math.pow(10, (-0.45 * Math.max(0, bass) - 0.25 * Math.max(0, treble)) / 20);
    }

    public boolean on() { return bShelf != null || tShelf != null; }

    /** in place, stereo, n frames from 0 */
    public void process(float[] L, float[] R, int n) {
        if (m.length < n) { m = new float[n]; h = new float[n]; }
        if (hb > 0 || ht > 0) {
            for (int i = 0; i < n; i++) m[i] = (L[i] + R[i]) * 0.5f;
            java.util.Arrays.fill(h, 0, n, 0f);
            if (hb > 0) harm(bLp, bHp, bLp2, true, hb, n);
            if (ht > 0) harm(tHp, tHp2, null, false, ht, n);
        }
        if (bShelf != null) { bShelf.process(0, L, 0, n); bShelf.process(1, R, 0, n); bPeak.process(0, L, 0, n); bPeak.process(1, R, 0, n); }
        if (tShelf != null) { tShelf.process(0, L, 0, n); tShelf.process(1, R, 0, n); tPeak.process(0, L, 0, n); tPeak.process(1, R, 0, n); }
        for (int i = 0; i < n; i++) { L[i] = (L[i] + h[i]) * pre; R[i] = (R[i] + h[i]) * pre; }
    }

    private float[] x = new float[0];

    /** band of m → envelope-normalised 2nd + 3rd harmonics → band-limited, added to h */
    private void harm(Biquad in, Biquad out1, Biquad out2, boolean bass, float amt, int n) {
        if (x.length < n) x = new float[n];
        System.arraycopy(m, 0, x, 0, n);
        in.process(0, x, 0, n);
        double e = bass ? eb : et;
        for (int i = 0; i < n; i++) {
            double v = x[i], a = Math.abs(v);
            e = a > e ? a : e * dec;
            if (e < 1e-4) e = 1e-4;
            double u = Math.max(-1, Math.min(1, v / e));
            double t2 = 2 * u * u - 1, t3 = (4 * u * u - 3) * u;
            x[i] = (float) (e * (bass ? 0.6 * t2 + 0.5 * t3 : 0.7 * t2 + 0.3 * t3));
        }
        if (bass) eb = e; else et = e;
        out1.process(0, x, 0, n);
        if (out2 != null) out2.process(0, x, 0, n);
        for (int i = 0; i < n; i++) h[i] += x[i] * amt;
    }
}
