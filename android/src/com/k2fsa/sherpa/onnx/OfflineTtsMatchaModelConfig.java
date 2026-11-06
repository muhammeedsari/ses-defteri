package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineTtsMatchaModelConfig {
    public String acousticModel="", vocoder="", lexicon="", tokens="", dataDir="", dictDir="";
    public float noiseScale=1.0f, lengthScale=1.0f;
}
