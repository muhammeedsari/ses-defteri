package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineCanaryModelConfig {
    public String encoder = "";
    public String decoder = "";
    public String srcLang = "en";
    public String tgtLang = "en";
    public boolean usePnc = true;
}
