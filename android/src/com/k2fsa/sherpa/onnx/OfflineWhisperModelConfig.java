package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineWhisperModelConfig {
    public String encoder = "";
    public String decoder = "";
    public String language = "en";
    public String task = "transcribe";
    public int tailPaddings = 1000;
    public boolean enableTokenTimestamps = false;
    public boolean enableSegmentTimestamps = false;
}
