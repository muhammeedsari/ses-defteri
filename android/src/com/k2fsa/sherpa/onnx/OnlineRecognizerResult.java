package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OnlineRecognizerResult {
    public final String text;
    public final String[] tokens;
    public final float[] timestamps;
    public final float[] ysProbs;
    public OnlineRecognizerResult(String text, String[] tokens, float[] timestamps, float[] ysProbs) { this.text = text; this.tokens = tokens; this.timestamps = timestamps; this.ysProbs = ysProbs; }
}
