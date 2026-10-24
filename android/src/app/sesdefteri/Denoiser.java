package app.sesdefteri;

import android.content.Context;

import app.sesdefteri.dsp.Node;

/**
 * AI speech denoiser: DPDFNet-2 (48 kHz, DeepFilterNet family) streamed through sherpa-onnx's C API
 * (the JNI part lives in libsdsep.so). The model works on mono at 48 kHz and answers ~40 ms late;
 * that delay is removed here, and a little of the original can be mixed back for a natural sound.
 */
final class Denoiser {
    private Denoiser() { }

    static native long create(String model, int threads);
    static native void destroy(long h);
    /** n < 0 flushes */
    static native float[] run(long h, float[] x, int n, int rate);

    static final int RATE = 48000, DELAY = 1920;

    static long open(Context c) throws Exception {
        if (!Separator.available()) throw new IllegalStateException("sep-unavailable");
        java.io.File m = c != null ? Separator.model(c, "dpdfnet.onnx") : new java.io.File(System.getProperty("sd.dn", ""));   // null: desktop tests
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        long h = create(m.getAbsolutePath(), threads);
        if (h == 0) throw new IllegalStateException("dn-model");
        return h;
    }

    /** ch channels at 48 kHz in, the same out; keep = share of the original mixed back (0 = all noise the model finds is removed) */
    static final class Stage extends Node {
        final long h;
        final int ch;
        final float keep;
        float[][] fifo;          // original input waiting for its denoised counterpart
        int rd, wr;
        long received, emitted, dropped;
        float[] mid = new float[0];
        float[][] out = new float[1][0];

        Stage(long h, int ch, double keep) {
            this.h = h; this.ch = ch; this.keep = (float) keep;
            fifo = new float[ch][1 << 16];
        }

        @Override public void push(float[][] b, int n) throws Exception {
            if (mid.length < n) mid = new float[n];
            if (wr + n > fifo[0].length) {
                int live = wr - rd, cap = fifo[0].length;
                while (live + n > cap) cap *= 2;
                for (int c = 0; c < ch; c++) { float[] f = new float[cap]; System.arraycopy(fifo[c], rd, f, 0, live); fifo[c] = f; }
                wr = live; rd = 0;
            }
            for (int i = 0; i < n; i++) {
                float v = 0;
                for (int c = 0; c < ch; c++) { v += b[c][i]; fifo[c][wr + i] = b[c][i]; }
                mid[i] = v / ch;
            }
            wr += n;
            received += n;
            take(Denoiser.run(h, mid, n, RATE));
        }

        private void take(float[] y) throws Exception {
            if (y == null || y.length == 0) return;
            int s = 0;
            if (dropped < DELAY) { s = (int) Math.min(y.length, DELAY - dropped); dropped += s; }
            int m = (int) Math.min(y.length - s, received - emitted);
            if (m <= 0) return;
            if (out[0].length < m) out = new float[ch][m];
            for (int i = 0; i < m; i++) {
                float e = y[s + i];
                for (int c = 0; c < ch; c++) {
                    float v = e + keep * fifo[c][rd + i];
                    out[c][i] = v > 1 ? 1 : v < -1 ? -1 : v;
                }
            }
            rd += m;
            emitted += m;
            next.push(out, m);
        }

        @Override public void end() throws Exception {
            // push silence through so the last 40 ms come out, then let the model empty itself
            float[] z = new float[4800];
            for (int i = 0; i < 3 && emitted < received; i++) take(Denoiser.run(h, z, z.length, RATE));
            if (emitted < received) take(Denoiser.run(h, null, -1, RATE));
            if (next != null) next.end();
        }
    }
}
