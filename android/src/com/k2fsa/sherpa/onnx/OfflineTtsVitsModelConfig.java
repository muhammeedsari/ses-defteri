package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineTtsVitsModelConfig {
    public String model="", lexicon="", tokens="", dataDir="", dictDir="";
    public float noiseScale=0.667f, noiseScaleW=0.8f, lengthScale=1.0f;
}
