package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OfflineSpeakerDiarizationConfig {
    public OfflineSpeakerSegmentationModelConfig segmentation = new OfflineSpeakerSegmentationModelConfig();
    public SpeakerEmbeddingExtractorConfig embedding = new SpeakerEmbeddingExtractorConfig();
    public FastClusteringConfig clustering = new FastClusteringConfig();
    public float minDurationOn = 0.2f;
    public float minDurationOff = 0.5f;
}
