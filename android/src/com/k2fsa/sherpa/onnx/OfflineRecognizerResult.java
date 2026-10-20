package com.k2fsa.sherpa.onnx;

/** Built by the native library through this exact constructor signature. */
public class OfflineRecognizerResult {
    public final String text;
    public final String[] tokens;
    public final float[] timestamps;
    public final String lang, emotion, event;
    public final float[] durations;

    public OfflineRecognizerResult(String text, String[] tokens, float[] timestamps, String lang, String emotion, String event, float[] durations) {
        this.text = text; this.tokens = tokens; this.timestamps = timestamps; this.lang = lang; this.emotion = emotion; this.event = event; this.durations = durations;
    }
}
