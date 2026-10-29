package com.k2fsa.sherpa.onnx;

/** Built by the native library through this exact constructor signature. */
public class OfflineSpeakerDiarizationSegment {
    public final float start, end, confidence;   // seconds; confidence −2 when not computed
    public final int speaker;                    // from 0

    public OfflineSpeakerDiarizationSegment(float start, float end, int speaker, float confidence) {
        this.start = start; this.end = end; this.speaker = speaker; this.confidence = confidence;
    }
}
