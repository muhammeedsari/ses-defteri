package app.sesdefteri.dsp;

/**
 * 5.0 mastering: the last touch on the whole mix, live and in the export. No look-ahead, so the timeline stays in sync.
 * Three bands (200 Hz / 3 kHz, subtractive crossover: unprocessed bands add back to the input exactly), each with its own
 * gentle compressor, then a tone curve, tape saturation for "tape", a makeup gain and a fast peak limiter at -1 dBFS.
 * A slow level follower then brings the mix to the style's loudness (quiet parts under -50 dB do not move it), so a quiet
 * recording and a loud one come out alike. Styles: natural, warm, tape, loud; amt 0…1 scales everything (0 = as it is).
 */
public final class Master {
    final Biquad lo1, lo2, hi1, hi2;
    final Biquad[] tone;
    final double[] thr = new double[3], ratio = new double[3], env = new double[3];
    final double atk, rel, drive, ceil, lRel, target, amt, fs;
    double lim = 1, ms, gdb;
    long t;
    final boolean tape;

    public Master(double fs, String style, double amt) {
        amt = Math.max(0, Math.min(1, amt));
        this.amt = amt; this.fs = fs;
        lo1 = Biquad.lowpass(2, fs, 200, 0.707); lo2 = Biquad.lowpass(2, fs, 200, 0.707);
        hi1 = Biquad.highpass(2, fs, 3000, 0.707); hi2 = Biquad.highpass(2, fs, 3000, 0.707);
        atk = 1 - Math.exp(-1 / (0.010 * fs));
        rel = 1 - Math.exp(-1 / (0.200 * fs));
        lRel = 1 - Math.exp(-1 / (0.050 * fs));
        ceil = Math.pow(10, -1.0 / 20);
        tape = "tape".equals(style);
        boolean loud = "loud".equals(style);
        // per band: low, mid, high
        double[] t0 = loud ? new double[] { -26, -24, -26 } : new double[] { -22, -20, -24 };
        double r0 = loud ? 4 : "warm".equals(style) ? 2.2 : 2;
        for (int b = 0; b < 3; b++) { thr[b] = t0[b] + 6 * (1 - amt); ratio[b] = 1 + (r0 - 1) * amt; }
        target = loud ? -13 : tape ? -17 : -18;   // dBFS RMS ≈ LUFS for speech and pop music
        ms = Math.pow(10, target / 10);
        drive = tape ? 1 + 1.5 * amt : loud ? 1 + 1.2 * amt : 0;   // loud: a soft clip so peaks round off instead of pulling the whole mix down
        if ("warm".equals(style)) tone = new Biquad[] { new Biquad(2).setLowShelf(fs, 140, 0.707, 2.5 * amt), new Biquad(2).setHighShelf(fs, 7000, 0.707, -1.5 * amt) };
        else if (tape) tone = new Biquad[] { new Biquad(2).setLowShelf(fs, 110, 0.707, 1.5 * amt), Biquad.lowpass(2, fs, 16000 - 5000 * amt, 0.707) };
        else if (loud) tone = new Biquad[] { new Biquad(2).setLowShelf(fs, 90, 0.707, 1.5 * amt), new Biquad(2).setHighShelf(fs, 9000, 0.707, 2 * amt) };
        else tone = new Biquad[] { new Biquad(2).setHighShelf(fs, 10000, 0.707, 1.2 * amt) };
    }

    private double comp(int b, double x0, double x1) {
        double p = (x0 * x0 + x1 * x1) * 0.5;
        env[b] += (p - env[b]) * (p > env[b] ? atk : rel);
        double lvl = 10 * Math.log10(env[b] + 1e-12), over = lvl - thr[b];
        if (over <= 0) return 1;
        return Math.pow(10, -over * (1 - 1 / ratio[b]) / 20);
    }

    /** in place, 2 channels */
    public void process(float[][] o, int n) {
        float[] L = o[0], R = o[1];
        for (int i = 0; i < n; i++) {
            double l = L[i], r = R[i];
            double ll = lo2.tick(0, lo1.tick(0, l)), lr = lo2.tick(1, lo1.tick(1, r));
            double hl = hi2.tick(0, hi1.tick(0, l)), hr = hi2.tick(1, hi1.tick(1, r));
            double ml = l - ll - hl, mr = r - lr - hr;
            double g0 = comp(0, ll, lr), g1 = comp(1, ml, mr), g2 = comp(2, hl, hr);
            l = ll * g0 + ml * g1 + hl * g2;
            r = lr * g0 + mr * g1 + hr * g2;
            for (Biquad q : tone) { l = q.tick(0, l); r = q.tick(1, r); }
            // level follower: quick for the first 2 s (so an export does not start at the old level), then slow
            double p = (l * l + r * r) * 0.5, ks = t++ < 2 * fs ? 1 / (0.3 * fs) : 1 / (3 * fs);
            if (p > 1e-5) ms += (p - ms) * ks;
            double want = Math.max(-6, Math.min(15, target - 10 * Math.log10(ms + 1e-12)));
            gdb += (want - gdb) * ks * 2;
            double mk = Math.pow(10, gdb * Math.min(1, amt * 2) / 20);   // from half strength on, the full way to the target
            l *= mk; r *= mk;
            if (drive > 0) { l = Math.tanh(l * drive) / drive; r = Math.tanh(r * drive) / drive; }
            double pk = Math.max(Math.abs(l), Math.abs(r)), wl = pk > ceil ? ceil / pk : 1;
            if (wl < lim) lim = wl; else lim += (wl - lim) * lRel;   // 5.14.1: instant attack, so a sudden peak can't clip
            l *= lim; r *= lim;
            L[i] = (float) (l > 1 ? 1 : l < -1 ? -1 : l);
            R[i] = (float) (r > 1 ? 1 : r < -1 ? -1 : r);
        }
    }
}
