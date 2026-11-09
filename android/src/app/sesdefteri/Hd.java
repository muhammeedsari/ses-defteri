package app.sesdefteri;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import app.sesdefteri.dsp.Fft;
import app.sesdefteri.dsp.Node;
import app.sesdefteri.dsp.Resampler;

/**
 * 4.12 "HD voice": LavaSR (Apache-2.0, ONNX export by Topping1) brings back the high frequencies a phone call, a cheap
 * microphone or a low-bitrate file lost. The model hears a 16 kHz version of the voice and paints the spectrum up to 24 kHz;
 * below the sound's own top frequency the original is kept untouched, only the missing band comes from the model.
 * ~56 MB, downloaded once from GitHub on request, then offline. Runs through ONNX Runtime's C API (libsdsep.so).
 */
final class Hd {
    private Hd() { }

    static native boolean open(String backbone, String head, int threads);
    static native void close();
    /** mel [80][frames] → real [1025][frames] then imaginary */
    static native float[] run(float[] mel, int frames);

    static final String URL = "https://github.com/Topping1/LavaSR-ONNX/releases/download/Alpha-v0.1/";
    static final String[] FILES = { "enhancer_backbone.onnx", "enhancer_backbone.onnx.data", "enhancer_spec_head.onnx", "enhancer_spec_head.onnx.data" };
    static final int RATE = 48000, NF = 2048, HOP = 512, BINS = NF / 2 + 1, MELS = 80, B = 256, C = 32;

    static File dir(Context c) { return c != null ? new File(c.getFilesDir(), "hd") : new File(System.getProperty("sd.hd", "")); }

    static boolean ready(Context c) {
        for (String f : FILES) if (!new File(dir(c), f).isFile()) return false;
        return true;
    }

    static JSONObject download(Context c, AudioTools.Progress p, AtomicBoolean cancel) throws Exception {
        File d = dir(c);
        d.mkdirs();
        long[] n = new long[FILES.length];
        long total = 0, done = 0;
        for (int i = 0; i < FILES.length; i++) total += n[i] = AsrModels.size(URL + FILES[i]);
        for (int i = 0; i < FILES.length; i++) {
            File f = new File(d, FILES[i]);
            if (!f.isFile() || f.length() != n[i]) AsrModels.fetch(URL + FILES[i], f, n[i], done, total, p, cancel);
            done += n[i];
        }
        return new JSONObject().put("ready", true);
    }

    /** the highest frequency (Hz) still within drop dB of the voice's main band (300–3000 Hz), from the speech-weighted long-term spectrum */
    static double bandwidth(double[] ltas, int fs, int fft, double drop) {
        double bw = fs / 2.0, ref = 0;
        int a = Math.max(1, 300 * fft / fs), b = 3000 * fft / fs;
        for (int k = a; k < b; k++) ref += ltas[k];
        ref = 10 * Math.log10(ref / (b - a) + 1e-20);
        for (int k = ltas.length - 1; k > b; k--) {
            double s = 0;
            int m = 0;
            for (int j = Math.max(b, k - 3); j <= Math.min(ltas.length - 1, k + 3); j++) { s += ltas[j]; m++; }
            if (10 * Math.log10(s / m + 1e-20) > ref - drop) { bw = (double) k * fs / fft; break; }
        }
        return bw;
    }

    static int threads() { return Device.threads(4); }

    /**
     * ch channels at 48 kHz in, the same out. Works in blocks of B frames (2.7 s) with C frames (0.34 s) of context on each side,
     * so any length fits in a few MB. fc: below it the original stays, above it the model's band (smooth 500 Hz crossover).
     */
    static final class Stage extends Node {
        final int ch;
        final float[] mask = new float[BINS], win = new float[NF];
        final float[][] fb = new float[MELS][];   // mel filters (Slaney, as the model was trained: 44.1 kHz scale, 0–8 kHz)
        final int[] fb0 = new int[MELS];
        float[][] x;                              // the original, absolute sample i at x[c][i - base]
        float[] w = new float[1 << 18];           // the 16 kHz version brought back to 48 kHz (what the model hears)
        long base, xEnd, wEnd, k;                 // k: next block
        final Node wSink;
        final Resampler down = new Resampler(1, (double) RATE, 16000.0), up = new Resampler(1, 16000.0, (double) RATE);
        final Fft fft = new Fft(NF);
        final double[] re = new double[NF], im = new double[NF];
        float[] mid = new float[0];
        float[][] out;

        Stage(int ch, double fc) {
            this.ch = ch;
            x = new float[ch][1 << 18];
            out = new float[ch][B * HOP];
            for (int i = 0; i < NF; i++) win[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / NF));
            for (int i = 0; i < BINS; i++) {
                double f = i * (RATE / 2.0) / (BINS - 1), t = Math.max(0, Math.min(1, (f - fc + 250) / 500));
                mask[i] = (float) (t * t * (3 - 2 * t));
            }
            double lo = mel(0), hi = mel(8000);
            double[] pts = new double[MELS + 2];
            for (int i = 0; i < MELS + 2; i++) pts[i] = hz(lo + (hi - lo) * i / (MELS + 1));
            for (int m = 0; m < MELS; m++) {
                double norm = 2 / (pts[m + 2] - pts[m]);
                int a = -1, e = -1;
                double[] row = new double[BINS];
                for (int i = 0; i < BINS; i++) {
                    double f = i * 22050.0 / (BINS - 1);
                    double v = Math.max(0, Math.min((f - pts[m]) / (pts[m + 1] - pts[m]), (pts[m + 2] - f) / (pts[m + 2] - pts[m + 1])));
                    if (v > 0) { if (a < 0) a = i; e = i; row[i] = v * norm; }
                }
                if (a < 0) { a = 0; e = 0; }
                fb0[m] = a;
                fb[m] = new float[e - a + 1];
                for (int i = a; i <= e; i++) fb[m][i - a] = (float) row[i];
            }
            wSink = new Node() {
                @Override public void push(float[][] b, int n) {
                    int at = (int) (wEnd - base);
                    if (at + n > w.length) w = java.util.Arrays.copyOf(w, Math.max(w.length * 2, at + n));
                    System.arraycopy(b[0], 0, w, at, n);
                    wEnd += n;
                }
                @Override public void end() { }
            };
            down.to(up).to(wSink);
        }

        static double mel(double f) { return f < 1000 ? f * 3 / 200 : 15 + Math.log(f / 1000) / (Math.log(6.4) / 27); }
        static double hz(double m) { return m < 15 ? m * 200 / 3 : 1000 * Math.exp((Math.log(6.4) / 27) * (m - 15)); }

        @Override public void push(float[][] b, int n) throws Exception {
            int at = (int) (xEnd - base);
            if (at + n > x[0].length) for (int c = 0; c < ch; c++) x[c] = java.util.Arrays.copyOf(x[c], Math.max(x[c].length * 2, at + n));
            if (mid.length < n) mid = new float[n];
            for (int i = 0; i < n; i++) {
                float v = 0;
                for (int c = 0; c < ch; c++) { v += b[c][i]; x[c][at + i] = b[c][i]; }
                mid[i] = v / ch;
            }
            xEnd += n;
            down.push(new float[][] { mid }, n);
            while (Math.min(xEnd, wEnd) >= hi(k)) block(false);
        }

        @Override public void end() throws Exception {
            down.end();
            while (k * B * HOP < xEnd) block(true);
            if (next != null) next.end();
        }

        static long lo(long k) { return (k * B - 2 - C) * HOP - (NF - HOP) / 2; }
        static long hi(long k) { return ((k + 1) * B + 1 + C) * HOP + NF - (NF - HOP) / 2; }

        private float xs(float[] a, long i, long end) { return i < base || i >= end ? 0 : a[(int) (i - base)]; }

        private void block(boolean last) throws Exception {
            final int F = B + 4 + 2 * C;
            final long t0 = k * B - 2 - C, s = k * B * HOP;
            float[] mel = new float[MELS * F];
            double[] mag = new double[BINS];
            for (int j = 0; j < F; j++) {
                long a = (t0 + j) * HOP - (NF - HOP) / 2;
                for (int i = 0; i < NF; i++) { re[i] = xs(w, a + i, wEnd) * win[i]; im[i] = 0; }
                fft.transform(re, im, false);
                for (int i = 0; i < BINS; i++) mag[i] = Math.sqrt(re[i] * re[i] + im[i] * im[i]);
                for (int m = 0; m < MELS; m++) {
                    double v = 0;
                    float[] r = fb[m];
                    for (int i = 0; i < r.length; i++) v += r[i] * mag[fb0[m] + i];
                    mel[m * F + j] = (float) Math.log(Math.max(v, 1e-5));
                }
            }
            float[] y = Hd.run(mel, F);
            if (y == null) throw new IllegalStateException("hd-model");
            final int L = B * HOP, IM = BINS * F;
            for (int c = 0; c < ch; c++) java.util.Arrays.fill(out[c], 0);
            for (int j = C; j < C + B + 4; j++) {
                long a = (t0 + j) * HOP - (NF - HOP) / 2;
                for (int c = 0; c < ch; c++) {
                    for (int i = 0; i < NF; i++) { re[i] = xs(x[c], a + i, xEnd) * win[i]; im[i] = 0; }
                    fft.transform(re, im, false);
                    for (int i = 0; i < BINS; i++) {
                        float m = mask[i];
                        if (m == 0) continue;
                        re[i] += m * (y[i * F + j] - re[i]);
                        im[i] += m * (y[IM + i * F + j] - im[i]);
                    }
                    im[0] = 0; im[BINS - 1] = 0;
                    for (int i = 1; i < BINS - 1; i++) { re[NF - i] = re[i]; im[NF - i] = -im[i]; }
                    fft.transform(re, im, true);
                    float[] o = out[c];
                    for (int i = 0; i < NF; i++) {
                        long p = a + i - s;
                        if (p >= 0 && p < L) o[(int) p] += (float) (re[i] * win[i] / (NF * 1.5));
                    }
                }
            }
            int n = (int) Math.min(L, xEnd - s);
            for (int c = 0; c < ch; c++) for (int i = 0; i < n; i++) { float v = out[c][i]; out[c][i] = v > 1 ? 1 : v < -1 ? -1 : v; }
            if (n > 0) next.push(out, n);
            k++;
            // drop what the next block no longer needs
            long drop = Math.min(lo(k), Math.min(xEnd, wEnd)) - base;
            if (drop > 0 && !last) {
                int d = (int) drop;
                for (int c = 0; c < ch; c++) System.arraycopy(x[c], d, x[c], 0, (int) (xEnd - base) - d);
                System.arraycopy(w, d, w, 0, (int) (wEnd - base) - d);
                base += d;
            }
        }
    }
}
