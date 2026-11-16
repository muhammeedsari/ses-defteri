package com.k2fsa.sherpa.onnx;
/** Boundary double: classification is deliberately not tested here. */
public class AudioTagging {
    public AudioTagging(AudioTaggingConfig config) { }
    public OfflineStream createStream() { return new OfflineStream(); }
    public AudioEvent[] compute(OfflineStream stream, int topK) { return new AudioEvent[0]; }
    public void release() { }
}
