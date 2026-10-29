package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineTtsKokoroModelConfig {
    public String model="", voices="", tokens="", dataDir="", lexicon="", lang="", dictDir="";
    public float lengthScale=1.0f;
}
