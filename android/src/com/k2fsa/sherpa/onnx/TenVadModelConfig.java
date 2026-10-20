package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class TenVadModelConfig {
    public String model = "";
    public float threshold = 0.5f;
    public float minSilenceDuration = 0.25f;
    public float minSpeechDuration = 0.25f;
    public int windowSize = 256;
    public float maxSpeechDuration = 5.0f;
}
