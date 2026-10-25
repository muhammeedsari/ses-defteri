package app.sesdefteri.dsp;

import java.util.Random;

/**
 * Ready-made sounds (3.3), synthesised on the phone instead of shipped as files: no licences, no download, almost no
 * space. Every sound is stereo at the given rate and the same every time (fixed random seeds).
 */
public final class Sfx {
    private Sfx() { }

    public static final String[] IDS = { "intro", "introSoft", "outro", "whoosh", "riser", "impact", "ding", "pop", "applause", "drumroll", "beep", "tick", "rain", "wind" };

    public static float[][] make(String id, int fs) throws Exception {
        switch (id) {
            case "intro": return reverb(intro(fs, new int[] { 60, 64, 67, 72, 76, 79, 84 }, 0.13, 3.2), fs, 0.6, 0.3);
            case "introSoft": return reverb(pad(fs, new int[] { 57, 64, 69, 72, 76 }, 4.5), fs, 0.8, 0.35);
            case "outro": return reverb(intro(fs, new int[] { 79, 76, 72, 67, 64, 60 }, 0.16, 3.4), fs, 0.6, 0.3);
            case "whoosh": return reverb(sweep(fs, 1.1, 300, 4000, 400, 0.9, 1), fs, 0.5, 0.2);
            case "riser": return reverb(riser(fs, 2.6), fs, 0.7, 0.25);
            case "impact": return reverb(impact(fs), fs, 0.9, 0.35);
            case "ding": return bell(fs, 1318.5, 2.6);
            case "pop": return pop(fs);
            case "applause": return reverb(applause(fs, 5), fs, 0.6, 0.2);
            case "drumroll": return reverb(drumroll(fs, 2.6), fs, 0.6, 0.2);
            case "beep": return beep(fs, 1.0);
            case "tick": return tick(fs, 4);
            case "rain": return rain(fs, 12);
            case "wind": return wind(fs, 12);
            default: throw new IllegalArgumentException("sfx " + id);
        }
    }

    static double hz(double midi) { return 440 * Math.pow(2, (midi - 69) / 12); }

    static float[][] buf(int fs, double sec) { return new float[2][(int) (fs * sec)]; }

    /** plucked synth notes one after another, ringing on */
    static float[][] intro(int fs, int[] notes, double step, double sec) {
        float[][] y = buf(fs, sec);
        for (int k = 0; k < notes.length; k++) {
            double f = hz(notes[k]), pan = 0.5 + 0.35 * Math.sin(k * 1.7);
            int s0 = (int) (k * step * fs), len = Math.min(y[0].length - s0, (int) (fs * 1.6));
            boolean last = k == notes.length - 1;
            for (int i = 0; i < len; i++) {
                double t = i / (double) fs, v = 0;
                for (int h = 1; h <= 7; h++) v += Math.sin(2 * Math.PI * f * h * t) / Math.pow(h, 1.4) * Math.exp(-t * (2.5 + h * 1.2) / (last ? 2 : 1));
                v *= 0.22 * Math.min(1, t * 400);
                y[0][s0 + i] += v * (1 - pan); y[1][s0 + i] += v * pan;
            }
        }
        return y;
    }

    /** a soft chord that swells in and fades */
    static float[][] pad(int fs, int[] notes, double sec) {
        float[][] y = buf(fs, sec);
        int n = y[0].length;
        for (int k = 0; k < notes.length; k++) for (int d = -1; d <= 1; d += 2) {
            double f = hz(notes[k]) * (1 + d * 0.003), ph = k * 0.9;
            for (int i = 0; i < n; i++) {
                double t = i / (double) fs, e = Math.min(1, t / 1.4) * Math.min(1, (sec - t) / 1.8);
                double v = (Math.sin(2 * Math.PI * f * t + ph) + 0.3 * Math.sin(4 * Math.PI * f * t)) * e * 0.06;
                y[d < 0 ? 0 : 1][i] += v;
            }
        }
        return y;
    }

    /** filtered noise whose centre moves from f0 up to f1 and back to f2 */
    static float[][] sweep(int fs, double sec, double f0, double f1, double f2, double gain, long seed) {
        float[][] y = buf(fs, sec);
        Random r = new Random(seed);
        int n = y[0].length;
        double[] lo = new double[2], bp = new double[2];
        for (int i = 0; i < n; i++) {
            double t = i / (double) n, f = t < 0.55 ? f0 * Math.pow(f1 / f0, t / 0.55) : f1 * Math.pow(f2 / f1, (t - 0.55) / 0.45);
            double g = 2 * Math.sin(Math.PI * Math.min(f, fs / 6.0) / fs), q = 0.35;
            double e = Math.sin(Math.PI * Math.pow(t, 0.8)) * gain;
            for (int c = 0; c < 2; c++) {
                double x = r.nextGaussian() * 0.5;
                double hp = x - lo[c] - q * bp[c];
                bp[c] += g * hp; lo[c] += g * bp[c];
                double pan = c == 0 ? 1 - t * 0.6 : 0.4 + t * 0.6;
                y[c][i] = (float) (bp[c] * e * pan);
            }
        }
        return y;
    }

    static float[][] riser(int fs, double sec) {
        float[][] y = sweep(fs, sec, 200, 9000, 9000, 0.7, 2);
        int n = y[0].length;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n, f = 110 * Math.pow(8, t);
            ph += 2 * Math.PI * f / fs;
            double v = (Math.sin(ph) + 0.5 * Math.sin(2 * ph + 0.4)) * 0.12 * t * t * Math.min(1, (1 - t) * 30);
            y[0][i] += v; y[1][i] += v;
        }
        return y;
    }

    static float[][] impact(int fs) {
        float[][] y = buf(fs, 2.0);
        Random r = new Random(3);
        int n = y[0].length;
        double ph = 0, lo = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) fs, f = 40 + 90 * Math.exp(-t * 18);
            ph += 2 * Math.PI * f / fs;
            double v = Math.tanh(2.2 * Math.sin(ph)) * 0.6 * Math.exp(-t * 2.6);
            lo += 0.25 * (r.nextGaussian() - lo);
            v += lo * 0.5 * Math.exp(-t * 14);
            y[0][i] = (float) v; y[1][i] = (float) v;
        }
        return y;
    }

    static float[][] bell(int fs, double f, double sec) {
        float[][] y = buf(fs, sec);
        double[] pr = { 1, 2.0, 2.76, 4.07, 5.4, 8.93 }, am = { 1, 0.45, 0.5, 0.25, 0.2, 0.08 }, dk = { 1.6, 2.4, 3.2, 4.5, 6, 9 };
        int n = y[0].length;
        for (int i = 0; i < n; i++) {
            double t = i / (double) fs, v = 0;
            for (int k = 0; k < pr.length; k++) v += am[k] * Math.sin(2 * Math.PI * f * pr[k] * t) * Math.exp(-t * dk[k]);
            v *= 0.22 * Math.min(1, t * 2000);
            y[0][i] = (float) v; y[1][i] = (float) (v * 0.92);
        }
        return y;
    }

    static float[][] pop(int fs) {
        float[][] y = buf(fs, 0.25);
        double ph = 0;
        for (int i = 0; i < y[0].length; i++) {
            double t = i / (double) fs;
            ph += 2 * Math.PI * (300 + 900 * Math.exp(-t * 60)) / fs;
            double v = Math.sin(ph) * 0.6 * Math.exp(-t * 35) * Math.min(1, t * 3000);
            y[0][i] = (float) v; y[1][i] = (float) v;
        }
        return y;
    }

    /** many single claps (short band-passed noise bursts) at random times and places, swelling and fading */
    static float[][] applause(int fs, double sec) throws Exception {
        float[][] y = buf(fs, sec);
        Random r = new Random(4);
        int n = y[0].length, clap = (int) (fs * 0.03);
        float[] one = new float[clap];
        for (double t = 0; t < sec - 0.05; t += 1.0 / (60 + r.nextDouble() * 40)) {
            double env = Math.min(1, t / 0.5) * Math.min(1, (sec - t) / 1.6);
            if (r.nextDouble() > env) continue;
            double f = 900 + r.nextDouble() * 1500, pan = r.nextDouble(), g = 0.25 + r.nextDouble() * 0.35;
            Biquad bp = Biquad.bandpass(1, fs, f, 1.2);
            for (int i = 0; i < clap; i++) one[i] = (float) (r.nextGaussian() * Math.exp(-i / (fs * 0.006)));
            bp.process(0, one, 0, clap);
            int s0 = (int) (t * fs);
            for (int i = 0; i < clap && s0 + i < n; i++) { y[0][s0 + i] += one[i] * g * (1 - pan); y[1][s0 + i] += one[i] * g * pan; }
        }
        return y;
    }

    static float[][] drumroll(int fs, double sec) throws Exception {
        float[][] y = buf(fs, sec + 1.5);
        Random r = new Random(5);
        int n = y[0].length, hit = (int) (fs * 0.06);
        float[] one = new float[hit];
        for (double t = 0; t < sec; t += 0.045 + r.nextDouble() * 0.008) {
            double g = 0.1 + 0.5 * Math.pow(t / sec, 1.5);
            for (int i = 0; i < hit; i++) one[i] = (float) ((r.nextGaussian() * 0.8 + Math.sin(2 * Math.PI * 190 * i / fs)) * Math.exp(-i / (fs * 0.012)));
            int s0 = (int) (t * fs);
            for (int i = 0; i < hit && s0 + i < n; i++) { y[0][s0 + i] += one[i] * g; y[1][s0 + i] += one[i] * g * 0.95; }
        }
        // the final hit with a cymbal
        int s0 = (int) (sec * fs);
        Biquad hp = Biquad.highpass(1, fs, 5000, 0.7);
        float[] cym = new float[n - s0];
        for (int i = 0; i < cym.length; i++) cym[i] = (float) (r.nextGaussian() * 0.45 * Math.exp(-i / (fs * 0.45)));
        hp.process(0, cym, 0, cym.length);
        for (int i = 0; i < cym.length; i++) {
            double b = i < fs * 0.25 ? (Math.sin(2 * Math.PI * 60 * i / fs) * 0.7 + r.nextGaussian() * 0.3) * Math.exp(-i / (fs * 0.06)) : 0;
            y[0][s0 + i] += cym[i] + b; y[1][s0 + i] += cym[i] * 0.9 + b;
        }
        return y;
    }

    static float[][] beep(int fs, double sec) {
        float[][] y = buf(fs, sec);
        for (int i = 0; i < y[0].length; i++) {
            double t = i / (double) fs, e = Math.min(1, Math.min(t, sec - t) * 200);
            float v = (float) (Math.sin(2 * Math.PI * 1000 * t) * 0.35 * e);
            y[0][i] = v; y[1][i] = v;
        }
        return y;
    }

    static float[][] tick(int fs, double sec) {
        float[][] y = buf(fs, sec);
        for (int k = 0; k < sec; k++) for (int h = 0; h < 2; h++) {
            int s0 = (int) ((k + h * 0.5) * fs);
            double f = h == 0 ? 3100 : 2500;
            for (int i = 0; i < fs / 50 && s0 + i < y[0].length; i++) {
                double v = Math.sin(2 * Math.PI * f * i / fs) * 0.3 * Math.exp(-i / (fs * 0.002));
                y[0][s0 + i] += v; y[1][s0 + i] += v;
            }
        }
        return y;
    }

    static float[][] rain(int fs, double sec) throws Exception {
        float[][] y = buf(fs, sec);
        Random r = new Random(6);
        int n = y[0].length;
        Biquad lp = Biquad.lowpass(2, fs, 7000, 0.7), hp = Biquad.highpass(2, fs, 400, 0.7);
        for (int c = 0; c < 2; c++) for (int i = 0; i < n; i++) y[c][i] = (float) (r.nextGaussian() * 0.06);
        // drops: tiny bright clicks
        for (int k = 0; k < sec * 120; k++) {
            int s0 = r.nextInt(n - 400), c = r.nextInt(2);
            double g = 0.05 + r.nextDouble() * 0.12, f = 2500 + r.nextDouble() * 4000;
            for (int i = 0; i < 300; i++) y[c][s0 + i] += Math.sin(2 * Math.PI * f * i / fs) * g * Math.exp(-i / 40.0);
        }
        for (int c = 0; c < 2; c++) { lp.process(c, y[c], 0, n); hp.process(c, y[c], 0, n); }
        fadeEnds(y, fs, 1.0);
        return y;
    }

    static float[][] wind(int fs, double sec) {
        float[][] y = buf(fs, sec);
        Random r = new Random(7);
        int n = y[0].length;
        double[] lo = new double[2], bp = new double[2];
        for (int i = 0; i < n; i++) {
            double t = i / (double) fs;
            double gust = 0.55 + 0.3 * Math.sin(2 * Math.PI * t / 4.3) + 0.15 * Math.sin(2 * Math.PI * t / 1.7 + 1);
            double f = 250 + 500 * gust, g = 2 * Math.sin(Math.PI * f / fs);
            for (int c = 0; c < 2; c++) {
                double x = r.nextGaussian(), hpv = x - lo[c] - 0.15 * bp[c];
                bp[c] += g * hpv; lo[c] += g * bp[c];
                y[c][i] = (float) (bp[c] * 0.09 * gust);
            }
        }
        fadeEnds(y, fs, 1.5);
        return y;
    }

    static void fadeEnds(float[][] y, int fs, double sec) {
        int n = y[0].length, f = Math.min(n / 2, (int) (fs * sec));
        for (int i = 0; i < f; i++) { float g = (float) i / f; for (float[] c : y) { c[i] *= g; c[n - 1 - i] *= g; } }
    }

    /** a room around the sound, then the result is brought to a peak of −1 dBFS */
    static float[][] reverb(float[][] x, int fs, double room, double mix) throws Exception {
        int n = x[0].length, pad = (int) (fs * (0.5 + room * 1.5));
        float[][] y = new float[2][n + pad];
        for (int c = 0; c < 2; c++) System.arraycopy(x[c], 0, y[c], 0, n);
        Stages.Reverb rv = new Stages.Reverb(2, fs, room, mix);
        Node.Collect col = new Node.Collect(2);
        rv.to(col);
        rv.push(y, y[0].length);
        rv.end();
        return col.result();
    }

    /** brought to its peak level (−1 dBFS, the bleep −6 dBFS) and the silent end trimmed off */
    public static float[][] finish(String id, float[][] y, int fs) {
        double top = "beep".equals(id) ? 0.5 : 0.89;
        double pk = 0;
        for (float[] c : y) for (float v : c) pk = Math.max(pk, Math.abs(v));
        double g = pk > 0 ? top / pk : 1;
        int end = y[0].length;
        while (end > fs / 10 && Math.abs(y[0][end - 1]) * g < 1e-4 && Math.abs(y[1][end - 1]) * g < 1e-4) end--;
        int f = Math.min(end, fs / 50);
        float[][] o = new float[2][end];
        for (int c = 0; c < 2; c++) for (int i = 0; i < end; i++) o[c][i] = (float) (y[c][i] * g * (i >= end - f ? (end - i) / (double) f : 1));
        return o;
    }
}
