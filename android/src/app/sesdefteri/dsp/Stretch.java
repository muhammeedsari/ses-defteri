package app.sesdefteri.dsp;

/**
 * 5.10: pitch change (with tempo) by Signalsmith Stretch (MIT, native libsdtp.so, 64-bit phones). In a desktop test
 * it kept music far cleaner than the phase vocoder for big pitch steps (+7 semitones: 3.7 vs 9.8 dB spectral error
 * after a round trip) and was about as good on voices. Pure tempo changes stay with TimePitch, which measured better there.
 * Output length is exactly the input length divided by speed, aligned with the input.
 */
public final class Stretch extends Node {
    static native long create(int ch, float rate, float semis, boolean formant);
    static native void destroy(long h);
    static native int seekLength(long h, float rate);
    static native void seek(long h, float[][] in, int n);
    static native void process(long h, float[][] in, int n, float[][] out, int m);

    private static Boolean ok;
    static synchronized boolean available() {
        if (ok == null) { try { System.loadLibrary("sdtp"); ok = true; } catch (Throwable e) { ok = false; } }
        return ok;
    }

    /** the best engine for this change */
    public static Node make(int ch, int fs, double speed, double semis, boolean formant) {
        if (Math.abs(semis) > 0.01 && ch <= 2 && available()) return new Stretch(ch, fs, speed, semis, formant);
        return new TimePitch(ch, fs, speed, semis, formant);
    }

    final int ch, S;
    final double speed;
    long h, fed, made, total;
    float[][] pre;
    int preN;
    float[][] out = new float[0][];

    Stretch(int ch, int fs, double speed, double semis, boolean formant) {
        this.ch = ch; this.speed = speed;
        h = create(ch, fs, (float) semis, formant);
        S = seekLength(h, (float) speed);
        pre = new float[ch][S];
    }

    private void run(float[][] b, int n) throws Exception {
        fed += n;
        long want = (long) Math.floor(fed / speed);
        int m = (int) (want - made);
        if (out.length == 0 || out[0].length < m) { out = new float[ch][Math.max(m, 4096)]; }
        process(h, b, n, out, m);
        long keep = Math.min(m, Math.round(total / speed) - made);
        made = want;
        if (keep > 0 && next != null) next.push(out, (int) keep);
    }

    @Override public void push(float[][] b, int n) throws Exception {
        total += n;
        int at = 0;
        if (pre != null) {   // the first S samples set the stretcher up so its first output lines up with the input's start
            int k = Math.min(n, S - preN);
            for (int c = 0; c < ch; c++) System.arraycopy(b[c], 0, pre[c], preN, k);
            preN += k; at = k;
            if (preN < S) return;
            seek(h, pre, S);
            pre = null;
        }
        if (at >= n) return;
        if (at > 0) { float[][] r = new float[ch][n - at]; for (int c = 0; c < ch; c++) System.arraycopy(b[c], at, r[c], 0, n - at); b = r; }
        run(b, n - at);
    }

    @Override public void end() throws Exception {
        try {
            if (pre != null) { seek(h, pre, S); pre = null; }   // shorter than the set-up: the rest is silence
            long target = Math.round(total / speed);
            float[][] z = new float[ch][4096];
            long extra = 0;
            // fed counts only what came after the set-up; feed silence until the output reaches the input's end
            while (made < target && extra < 40L * S + 1000000) { long before = made; run(z, 4096); extra += 4096; if (made == before) break; }
        } finally {
            destroy(h); h = 0;
        }
        if (next != null) next.end();
    }

    /** 5.14.1: a cancelled job never reaches end(); free the native stretcher when this node is collected */
    @Override protected void finalize() throws Throwable {
        try { if (h != 0) { destroy(h); h = 0; } } finally { super.finalize(); }
    }
}
