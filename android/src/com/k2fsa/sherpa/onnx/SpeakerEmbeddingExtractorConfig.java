package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class SpeakerEmbeddingExtractorConfig {
    public String model = "";
    public int numThreads = 1;
    public boolean debug = false;
    public String provider = "cpu";
}
