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

    static native long createOff(String model, int threads);
    static native void destroyOff(long h);
    /** n samples in, the same span out, aligned */
    static native float[] runOff(long h, float[] x, int n, int rate);

    static java.io.File modelFile(Context c) throws Exception {
        if (!Separator.available()) throw new IllegalStateException("sep-unavailable");
        return c != null ? Separator.model(c, "dpdfnet.onnx") : new java.io.File(System.getProperty("sd.dn", ""));
    }

    /**
     * 4.15: whole 15 s chunks through the offline model, several at once (one model per worker, 1 thread each):
     * ~1.45x faster per core than streaming, and the workers add up. Each chunk starts 0.5 s early (the model settles)
     * and ends 0.25 s late, faded into the next one; seams measured at -35 dB against one uninterrupted run.
     * ch channels at 48 kHz in, the same out; keep = share of the original mixed back.
     */
    static final class Par extends Node {
        static final int L = 15 * RATE, P = RATE, Q = RATE / 2;
        final int ch, W;
        final float keep;
        final long[] h;
        final java.util.concurrent.ExecutorService pool;
        float[][] x;            // the original, absolute sample i at x[c][i - base]
        float[] mid;
        long base, n, k;        // n: samples received, k: next chunk
        float[] tail;           // the previous chunk's last Q samples (to fade from)
        float[][] out;

        Par(Context c, int ch, double keep) throws Exception {
            this.ch = ch; this.keep = (float) keep;
            String m = modelFile(c).getAbsolutePath();
            W = Device.workers();
            h = new long[W];
            try {
                for (int i = 0; i < W; i++) if ((h[i] = createOff(m, 1)) == 0) throw new IllegalStateException("dn-model");
            } catch (Exception e) { close(); throw e; }
            pool = java.util.concurrent.Executors.newFixedThreadPool(W);
            int cap = W * L + P + Q + 8192;
            x = new float[ch][cap];
            mid = new float[cap];
            out = new float[ch][L];
        }

        void close() {
            if (pool != null) pool.shutdownNow();
            for (int i = 0; i < h.length; i++) if (h[i] != 0) { destroyOff(h[i]); h[i] = 0; }
        }

        @Override public void push(float[][] b, int cnt) throws Exception {
            for (int o = 0; o < cnt; ) {
                int at = (int) (n - base), m = Math.min(cnt - o, mid.length - at);
                if (m <= 0) { batch(false); continue; }
                for (int i = 0; i < m; i++) {
                    float v = 0;
                    for (int c = 0; c < ch; c++) { float s = b[c][o + i]; x[c][at + i] = s; v += s; }
                    mid[at + i] = v / ch;
                }
                n += m; o += m;
                if (n >= (k + W) * L + Q) batch(false);
            }
        }

        @Override public void end() throws Exception {
            while (k * L < n) batch(true);
            close();
            if (next != null) next.end();
        }

        /** the chunks that are complete (all of them at the end), side by side, then out in order */
        private void batch(boolean last) throws Exception {
            int cnt = 0;
            while (cnt < W && ((k + cnt + 1) * L + Q <= n || (last && (k + cnt) * L < n))) cnt++;
            if (cnt == 0) return;
            java.util.List<java.util.concurrent.Future<float[]>> fs = new java.util.ArrayList<>();
            for (int j = 0; j < cnt; j++) {
                final long s = (k + j) * L, a = Math.max(0, s - P), e = Math.min(n, s + L + Q);
                final float[] seg = java.util.Arrays.copyOfRange(mid, (int) (a - base), (int) (e - base));
                final long hh = h[j];
                fs.add(pool.submit(new java.util.concurrent.Callable<float[]>() {
                    @Override public float[] call() { return runOff(hh, seg, seg.length, RATE); }
                }));
            }
            for (int j = 0; j < cnt; j++) {
                float[] y = fs.get(j).get();
                if (y == null) throw new IllegalStateException("dn-model");
                long s = k * L, a = Math.max(0, s - P);
                int off = (int) (s - a), len = (int) Math.min(L, n - s);
                for (int i = 0; i < len; i++) {
                    float d = off + i < y.length ? y[off + i] : 0;
                    if (tail != null && i < Q) { float r = (float) i / Q; d = tail[i] * (1 - r) + d * r; }
                    for (int c = 0; c < ch; c++) {
                        float v = d + keep * x[c][(int) (s - base) + i];
                        out[c][i] = v > 1 ? 1 : v < -1 ? -1 : v;
                    }
                }
                tail = null;
                if (s + L < n) {
                    tail = new float[Q];
                    for (int i = 0; i < Q; i++) tail[i] = off + L + i < y.length ? y[off + L + i] : 0;
                }
                next.push(out, len);
                k++;
            }
            // keep from the next chunk's lead-in on
            long keepFrom = Math.max(base, Math.min(n, k * L - P));
            int d = (int) (keepFrom - base), live = (int) (n - keepFrom);
            if (d > 0) {
                for (int c = 0; c < ch; c++) System.arraycopy(x[c], d, x[c], 0, live);
                System.arraycopy(mid, d, mid, 0, live);
                base = keepFrom;
            }
        }
    }

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
