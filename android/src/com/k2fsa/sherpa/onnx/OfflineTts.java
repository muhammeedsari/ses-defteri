package com.k2fsa.sherpa.onnx;

/** Java version of the sherpa-onnx Kotlin OfflineTts (same native methods). */
public class OfflineTts {
    private long ptr;

    /** called with each finished piece of audio; return 1 to go on, 0 to stop */
    public interface Callback { Integer invoke(float[] samples); }

    public OfflineTts(OfflineTtsConfig config) {
        ptr = newFromFile(config);
        if (ptr == 0) throw new IllegalStateException("Invalid OfflineTtsConfig: failed to create native OfflineTts");
    }

    public int sampleRate() { return getSampleRate(ptr); }

    public GeneratedAudio generate(String text, int sid, float speed, Callback cb) {
        return cb == null ? generateImpl(ptr, text, sid, speed) : generateWithCallbackImpl(ptr, text, sid, speed, cb);
    }

    public void release() { if (ptr != 0) { delete(ptr); ptr = 0; } }

    private native long newFromFile(OfflineTtsConfig config);
    private native void delete(long ptr);
    private native int getSampleRate(long ptr);
    private native GeneratedAudio generateImpl(long ptr, String text, int sid, float speed);
    private native GeneratedAudio generateWithCallbackImpl(long ptr, String text, int sid, float speed, Object callback);

    static { System.loadLibrary("sherpa-onnx-jni"); }
}
