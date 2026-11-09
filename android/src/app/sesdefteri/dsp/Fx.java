package app.sesdefteri.dsp;

/**
 * 4.13 creative stereo effects. Every one takes 1 or 2 channels and gives 2, same length, no added latency.
 * EightD: the voice circles the head. Widen: a wider stereo picture (mono stays mono-compatible: L + R = 2x).
 * Double: two slightly late, slightly wobbling copies left and right (the classic double-tracked vocal).
 * LoFi: band-limited, gently saturated, a slow tape wobble, quiet vinyl crackle.
 */
public final class Fx {
    private Fx() { }

    /** a short fractional delay line (linear interpolation) */
    static final class Line {
        final float[] b;
        int w;
        Line(int n) { b = new float[n]; }
        void put(float x) { b[w] = x; w = (w + 1) % b.length; }
        /** d samples ago (d ≥ 1) */
        float get(double d) {
            double p = w - d;
            while (p < 0) p += b.length;
            int i = (int) p;
            double f = p - i;
            return (float) (b[i] * (1 - f) + b[(i + 1) % b.length] * f);
        }
    }

    abstract static class Stereo extends Node {
        final int ch;
        float[][] o = new float[2][0];
        Stereo(int ch) { this.ch = ch; }
        @Override public final void push(float[][] b, int n) throws Exception {
            if (o[0].length < n) o = new float[2][n];
            for (int i = 0; i < n; i++) {
                float l = b[0][i], r = ch > 1 ? b[1][i] : l;
                frame(l, r, i);
            }
            next.push(o, n);
        }
        abstract void frame(float l, float r, int i);
    }

    /** period: seconds per turn round the head */
    public static final class EightD extends Stereo {
        final double w, itd;
        final Line dl;
        final Biquad shade;
        long t;
        public EightD(int ch, int fs, double period) {
            super(ch);
            w = 2 * Math.PI / (period * fs);
            itd = 0.0006 * fs;                        // up to 0.6 ms later at the far ear
            dl = new Line((int) itd + 4);
            shade = Biquad.lowpass(2, fs, 4500, 0.707); // the far ear hears it a little darker
        }
        @Override void frame(float l, float r, int i) {
            float x = (l + r) * 0.5f;
            dl.put(x);
            double s = Math.sin(w * t++);             // -1 left … +1 right
            double gl = Math.cos((s + 1) * Math.PI / 4), gr = Math.sin((s + 1) * Math.PI / 4);
            float late = dl.get(1 + itd * Math.abs(s));
            float yl = s > 0 ? late : x, yr = s < 0 ? late : x;
            // the side turned away is shaded; mix in proportion to how far it is turned
            double dk = (float) Math.abs(s) * 0.6;
            double fl = shade.tick(0, yl), fr = shade.tick(1, yr);
            yl = (float) (s > 0 ? yl + (fl - yl) * dk : yl);
            yr = (float) (s < 0 ? yr + (fr - yr) * dk : yr);
            o[0][i] = (float) (yl * gl * 1.41421356);
            o[1][i] = (float) (yr * gr * 1.41421356);
        }
    }

    /** amt 0…1 */
    public static final class Widen extends Stereo {
        final double k;
        final Line dl;
        final Biquad hp;
        final int d;
        public Widen(int ch, int fs, double amt) {
            super(ch);
            k = amt;
            d = Math.round(fs * 0.017f);
            dl = new Line(d + 2);
            hp = Biquad.highpass(1, fs, 350, 0.707);   // the bass stays in the middle
        }
        @Override void frame(float l, float r, int i) {
            float m = (l + r) * 0.5f, s = (l - r) * 0.5f;
            dl.put((float) hp.tick(0, m));
            if (ch > 1) s *= (float) (1 + 1.5 * k);
            s += (float) (dl.get(d) * 0.8 * k);
            o[0][i] = m + s;
            o[1][i] = m - s;
        }
    }

    public static final class Double extends Stereo {
        final Line dl;
        final double fs, w1, w2;
        long t;
        public Double(int ch, int fs) {
            super(ch);
            this.fs = fs;
            dl = new Line((int) (fs * 0.04));
            w1 = 2 * Math.PI * 0.31 / fs;
            w2 = 2 * Math.PI * 0.23 / fs;
        }
        @Override void frame(float l, float r, int i) {
            float x = (l + r) * 0.5f;
            dl.put(x);
            double a = fs * (0.019 + 0.0022 * Math.sin(w1 * t)), b = fs * (0.027 + 0.0026 * Math.sin(w2 * t + 1.3));
            t++;
            o[0][i] = l * 0.8f + dl.get(a) * 0.6f;
            o[1][i] = r * 0.8f + dl.get(b) * 0.6f;
        }
    }

    public static final class LoFi extends Stereo {
        final Biquad hp, lp, lp2;
        final Line dl;
        final double fs, ww;
        long t, seed = 0x2545F4914F6CDD1DL;
        double crack;
        public LoFi(int ch, int fs) {
            super(ch);
            this.fs = fs;
            hp = Biquad.highpass(2, fs, 110, 0.707);
            lp = Biquad.lowpass(2, fs, 4200, 0.6);
            lp2 = Biquad.lowpass(2, fs, 5200, 0.707);
            dl = new Line((int) (fs * 0.01));
            ww = 2 * Math.PI * 0.55 / fs;
        }
        double rnd() { seed ^= seed << 13; seed ^= seed >>> 7; seed ^= seed << 17; return (seed >>> 11) * (1.0 / (1L << 53)); }
        @Override void frame(float l, float r, int i) {
            // tape wobble on the middle only: a gentle pitch drift, the stereo picture stays
            float m = (l + r) * 0.5f, s = (l - r) * 0.5f;
            dl.put(m);
            m = dl.get(fs * (0.004 + 0.0009 * Math.sin(ww * t++)));
            double yl = m + s * 0.6, yr = m - s * 0.6;
            yl = lp2.tick(0, lp.tick(0, hp.tick(0, yl)));
            yr = lp2.tick(1, lp.tick(1, hp.tick(1, yr)));
            yl = Math.tanh(yl * 1.6) / 1.6 * 1.25;
            yr = Math.tanh(yr * 1.6) / 1.6 * 1.25;
            // vinyl: soft hiss and now and then a click that dies away in a millisecond or two
            if (rnd() < 6.0 / fs) crack = (rnd() - 0.5) * 0.12;
            crack *= 0.985;
            double hiss = (rnd() - 0.5) * 0.004;
            o[0][i] = (float) (yl + crack + hiss);
            o[1][i] = (float) (yr + crack + hiss);
        }
    }
}
