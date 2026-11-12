package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OnlineRecognizerConfig {
    public FeatureConfig featConfig = new FeatureConfig();
    public OnlineModelConfig modelConfig = new OnlineModelConfig();
    public OnlineLMConfig lmConfig = new OnlineLMConfig();
    public OnlineCtcFstDecoderConfig ctcFstDecoderConfig = new OnlineCtcFstDecoderConfig();
    public HomophoneReplacerConfig hr = new HomophoneReplacerConfig();
    public EndpointConfig endpointConfig = new EndpointConfig();
    public boolean enableEndpoint = true;
    public String decodingMethod = "greedy_search";
    public int maxActivePaths = 4;
    public String hotwordsFile = "";
    public float hotwordsScore = 1.5f;
    public String ruleFsts = "";
    public String ruleFars = "";
    public float blankPenalty = 0.0f;
}
