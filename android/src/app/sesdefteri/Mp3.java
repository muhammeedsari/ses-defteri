package app.sesdefteri;

/** MP3 encoder (LAME through libsdmp3.so). Only shipped for 64-bit ARM phones. */
final class Mp3 {
    private Mp3() { }

    static native long init(int rate, int ch, int kbps);
    static native byte[] encode(long h, short[] pcm, int frames, int ch);
    static native byte[] flush(long h);
    static native void close(long h);

    private static Boolean ok;

    static synchronized boolean available() {
        if (ok == null) {
            try { System.loadLibrary("sdmp3"); ok = true; } catch (Throwable e) { ok = false; }
        }
        return ok;
    }
}
