package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineTtsConfig {
    public OfflineTtsModelConfig model=new OfflineTtsModelConfig();
    public String ruleFsts="", ruleFars="";
    public int maxNumSentences=1;
    public float silenceScale=0.2f;
}
