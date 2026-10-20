package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class VadModelConfig {
    public SileroVadModelConfig sileroVadModelConfig = new SileroVadModelConfig();
    public TenVadModelConfig tenVadModelConfig = new TenVadModelConfig();
    public int sampleRate = 16000;
    public int numThreads = 1;
    public String provider = "cpu";
    public boolean debug = false;
}
