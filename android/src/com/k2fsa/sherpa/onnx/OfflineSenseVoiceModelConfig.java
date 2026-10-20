package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineSenseVoiceModelConfig {
    public String model = "";
    public String language = "";
    public boolean useInverseTextNormalization = true;
    public QnnConfig qnnConfig = new QnnConfig();
}
