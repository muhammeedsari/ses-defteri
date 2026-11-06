package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineTtsPocketModelConfig {
    public String lmFlow="", lmMain="", encoder="", decoder="", textConditioner="", vocabJson="", tokenScoresJson="";
    public int voiceEmbeddingCacheCapacity=50;
}
