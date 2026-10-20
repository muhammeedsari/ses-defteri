package com.k2fsa.sherpa.onnx;

/** Java version of the sherpa-onnx Kotlin Vad (same native methods). */
public class Vad {
    private long ptr;

    public Vad(Object assets, VadModelConfig config) {
        ptr = assets != null ? newFromAsset((android.content.res.AssetManager) assets, config) : newFromFile(config);
        if (ptr == 0) throw new IllegalStateException("Invalid VadConfig: failed to create native Vad");
    }

    public void acceptWaveform(float[] samples) { acceptWaveform(ptr, samples); }
    public boolean empty() { return empty(ptr); }
    public void pop() { pop(ptr); }
    public SpeechSegment front() { return front(ptr); }
    public void clear() { clear(ptr); }
    public boolean isSpeechDetected() { return isSpeechDetected(ptr); }
    public void reset() { reset(ptr); }
    public void flush() { flush(ptr); }
    public void release() { if (ptr != 0) { delete(ptr); ptr = 0; } }

    private native void delete(long ptr);
    private native long newFromAsset(android.content.res.AssetManager assetManager, VadModelConfig config);
    private native long newFromFile(VadModelConfig config);
    private native void acceptWaveform(long ptr, float[] samples);
    private native float compute(long ptr, float[] samples);
    private native boolean empty(long ptr);
    private native void pop(long ptr);
    private native void clear(long ptr);
    private native SpeechSegment front(long ptr);
    private native boolean isSpeechDetected(long ptr);
    private native void reset(long ptr);
    private native void flush(long ptr);

    static { System.loadLibrary("sherpa-onnx-jni"); }
}
