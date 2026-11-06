package com.k2fsa.sherpa.onnx;

/** Java version of the sherpa-onnx Kotlin OfflineSpeakerDiarization (same native methods). */
public class OfflineSpeakerDiarization {
    private long ptr;

    /** the callback object needs a method {@code public Integer invoke(int done, int total, long arg)} (0 = go on) */
    public interface Callback { Integer invoke(int done, int total, long arg); }

    public OfflineSpeakerDiarization(OfflineSpeakerDiarizationConfig config) {
        ptr = newFromFile(config);
        if (ptr == 0) throw new IllegalStateException("Invalid OfflineSpeakerDiarizationConfig: failed to create native OfflineSpeakerDiarization");
    }

    public int sampleRate() { return getSampleRate(ptr); }

    public OfflineSpeakerDiarizationSegment[] process(float[] samples, Callback cb) {
        return cb == null ? process(ptr, samples) : processWithCallback(ptr, samples, cb, 0);
    }

    public void release() { if (ptr != 0) { delete(ptr); ptr = 0; } }

    private native void delete(long ptr);
    private native long newFromFile(OfflineSpeakerDiarizationConfig config);
    private native int getSampleRate(long ptr);
    private native OfflineSpeakerDiarizationSegment[] process(long ptr, float[] samples);
    private native OfflineSpeakerDiarizationSegment[] processWithCallback(long ptr, float[] samples, Object callback, long arg);

    static { System.loadLibrary("sherpa-onnx-jni"); }
}
