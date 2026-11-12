package com.k2fsa.sherpa.onnx;

public class OnlineStream {
    long ptr;

    OnlineStream(long ptr) {
        if (ptr == 0) throw new IllegalStateException("Failed to create native OnlineStream");
        this.ptr = ptr;
    }

    public void acceptWaveform(float[] samples, int sampleRate) { acceptWaveform(ptr, samples, sampleRate); }

    public void inputFinished() { inputFinished(ptr); }

    public void release() { if (ptr != 0) { delete(ptr); ptr = 0; } }

    private native void acceptWaveform(long ptr, float[] samples, int sampleRate);
    private native void inputFinished(long ptr);
    private native void delete(long ptr);

    static { System.loadLibrary("sherpa-onnx-jni"); }
}
