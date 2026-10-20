package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineRecognizerConfig {
    public FeatureConfig featConfig = new FeatureConfig();
    public OfflineModelConfig modelConfig = new OfflineModelConfig();
    public HomophoneReplacerConfig hr = new HomophoneReplacerConfig();
    public String decodingMethod = "greedy_search";
    public int maxActivePaths = 4;
    public String hotwordsFile = "";
    public float hotwordsScore = 1.5f;
    public String ruleFsts = "";
    public String ruleFars = "";
    public float blankPenalty = 0.0f;
}
