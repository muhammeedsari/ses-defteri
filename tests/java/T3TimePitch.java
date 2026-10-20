import app.sesdefteri.dsp.*;

/** Speed / pitch change and formant preservation, measured on a synthetic vowel and on speech. */
public class T3TimePitch extends TestUtil {
    static final int fs = 48000;
    static final double[] FORM = { 700, 1220, 2600 };

    static float[] vowel(double f0, double secs) {
        int n = (int) (secs * fs);
        float[] x = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) { ph += f0 / fs; if (ph >= 1) { ph -= 1; x[i] = 1; } }
        float[] y = new float[n];
        double[] bw = { 90, 110, 160 };
        for (int k = 0; k < 3; k++) {
            float[] t = x.clone();
            Biquad b = Biquad.bandpass(1, fs, FORM[k], FORM[k] / bw[k]);
            b.process(0, t, 0, n);
            for (int i = 0; i < n; i++) y[i] += t[i] * (k == 0 ? 1 : k == 1 ? 0.7 : 0.4);
        }
        double m = 0; for (float v : y) m = Math.max(m, Math.abs(v));
        for (int i = 0; i < n; i++) y[i] *= 0.4 / m;
        return y;
    }

    static double f0(float[] x) {
        int mid = x.length / 2, W = 4096;
        double best = 0; int bl = 0;
        for (int lag = fs / 500; lag < fs / 60; lag++) {
            double s = 0, e1 = 0, e2 = 0;
            for (int i = mid; i < mid + W; i++) { s += x[i] * x[i + lag]; e1 += x[i] * x[i]; e2 += x[i + lag] * x[i + lag]; }
            double r = s / Math.sqrt(e1 * e2 + 1e-20);
            if (r > best) { best = r; bl = lag; }
        }
        // parabolic refinement
        return (double) fs / bl;
    }

    /** LPC envelope peaks (averaged over frames) nearest to each expected formant region */
    static double[] formants(float[] x48) throws Exception {
        // standard practice: LPC of order 2 + fs/1000 on audio resampled to 12 kHz
        float[] x = Resampler.resample(new float[][] { x48 }, 48000, 12000)[0];
        final int fs = 12000;
        int N = 512, order = 14;
        double[] envAvg = new double[N / 2];
        int frames = 0;
        for (int st = x.length / 4; st + N < x.length * 3 / 4; st += N / 2) {
            double[] w = new double[N];
            for (int i = 0; i < N; i++) w[i] = x[st + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
            double[] r = new double[order + 1];
            for (int l = 0; l <= order; l++) { double s = 0; for (int i = l; i < N; i++) s += w[i] * w[i - l]; r[l] = s; }
            r[0] *= 1.0001;
            double[] a = new double[order + 1]; a[0] = 1; double err = r[0];
            for (int i = 1; i <= order; i++) {
                double acc = r[i]; for (int j = 1; j < i; j++) acc += a[j] * r[i - j];
                double k = -acc / err; double[] na = a.clone();
                for (int j = 1; j < i; j++) na[j] = a[j] + k * a[i - j];
                na[i] = k; a = na; err *= 1 - k * k;
            }
            for (int b = 0; b < N / 2; b++) {
                double om = Math.PI * b / (N / 2), rr = 0, ii = 0;
                for (int j = 0; j <= order; j++) { rr += a[j] * Math.cos(om * j); ii -= a[j] * Math.sin(om * j); }
                envAvg[b] += -10 * Math.log10(rr * rr + ii * ii);
            }
            frames++;
        }
        double[][] ranges = { { 450, 950 }, { 950, 1800 }, { 1900, 3600 } };
        double[] out = new double[3];
        for (int f = 0; f < 3; f++) {
            double best = -1e9; int bb = 0;
            for (int b = 0; b < N / 2; b++) {
                double hz = b * (fs / 2.0) / (N / 2);
                if (hz < ranges[f][0] || hz > ranges[f][1]) continue;
                if (envAvg[b] > best) { best = envAvg[b]; bb = b; }
            }
            out[f] = bb * (fs / 2.0) / (N / 2);
        }
        return out;
    }

    static float[] run(float[] x, double speed, double st, boolean keep) throws Exception {
        TimePitch tp = new TimePitch(1, fs, speed, st, keep);
        return Node.apply(new float[][] { x }, tp, tp, 1)[0];
    }

    static double maxErr(double[] a, double[] b) { double m = 0; for (int i = 0; i < a.length; i++) m = Math.max(m, Math.abs(a[i] / b[i] - 1)); return m; }

    public static void main(String[] args) throws Exception {
        float[] v = vowel(140, 3.0);
        double f0In = f0(v);
        double[] fIn = formants(v);
        System.out.printf("  input: F0 %.1f Hz, formants %.0f / %.0f / %.0f Hz%n", f0In, fIn[0], fIn[1], fIn[2]);

        float[] fast = run(v, 1.5, 0, true);
        double r = (double) fast.length / v.length;
        check("speed 1.5x: length 1/1.5", Math.abs(r - 1 / 1.5) < 0.005, f(r));
        check("speed 1.5x: pitch unchanged", Math.abs(f0(fast) / f0In - 1) < 0.01, f(f0(fast)) + " Hz");
        check("speed 1.5x: formants unchanged (<5%)", maxErr(formants(fast), fIn) < 0.05, java.util.Arrays.toString(formants(fast)));

        float[] up = run(v, 1.0, 4, true);
        double ratio = f0(up) / f0In, want = Math.pow(2, 4 / 12.0);
        check("+4 semitones: pitch x1.26", Math.abs(ratio / want - 1) < 0.01, f(ratio));
        check("+4 semitones: length unchanged", Math.abs((double) up.length / v.length - 1) < 0.005, "" + up.length);
        double[] fu = formants(up);
        check("+4 st with formants kept: formants within 8%", maxErr(fu, fIn) < 0.08, java.util.Arrays.toString(fu));
        double[] fo = formants(run(v, 1.0, 4, false));
        double shift = 0; for (int i = 0; i < 3; i++) shift += fo[i] / fIn[i] / 3;
        check("+4 st without formant keeping: formants move up (~x1.26)", shift > 1.15, "avg ratio " + f(shift));

        float[] down = run(v, 0.8, -3, true);
        check("-3 st, speed 0.8: pitch x0.84", Math.abs(f0(down) / f0In / Math.pow(2, -3 / 12.0) - 1) < 0.01, f(f0(down) / f0In));
        check("-3 st, speed 0.8: length x1.25", Math.abs((double) down.length / v.length - 1.25) < 0.01, f((double) down.length / v.length));
        check("-3 st with formants kept: within 8%", maxErr(formants(down), fIn) < 0.08, java.util.Arrays.toString(formants(down)));

        float[] vf = vowel(230, 3.0);
        double[] fF = formants(vf);
        double[] fFu = formants(run(vf, 1.0, 4, true));
        check("higher voice (230 Hz) +4 st: formants within 8%", maxErr(fFu, fF) < 0.08, java.util.Arrays.toString(fF) + " -> " + java.util.Arrays.toString(fFu));
        Wav sp = wav("t_clean.wav");
        float[] s2 = run(sp.data[0], 1.25, 0, true);
        double L0 = Loudness.of(sp.data, fs), L1 = Loudness.of(new float[][] { s2 }, fs);
        check("speech 1.25x: loudness kept within 1 dB", Math.abs(L1 - L0) < 1, f(L0) + " -> " + f(L1));
        check("speech 1.25x: length", Math.abs((double) s2.length / sp.data[0].length - 0.8) < 0.003, f((double) s2.length / sp.data[0].length));
        done();
    }
}
