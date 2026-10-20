package com.k2fsa.sherpa.onnx;

/** Java version of the sherpa-onnx Kotlin OfflineRecognizer (same native methods). */
public class OfflineRecognizer {
    private long ptr;

    /** @param assets android.content.res.AssetManager or null to read model files from paths */
    public OfflineRecognizer(Object assets, OfflineRecognizerConfig config) {
        ptr = assets != null ? newFromAsset((android.content.res.AssetManager) assets, config) : newFromFile(config);
        if (ptr == 0) throw new IllegalStateException("Invalid OfflineRecognizerConfig: failed to create native OfflineRecognizer");
    }

    public OfflineStream createStream() { return new OfflineStream(createStream(ptr)); }

    public void decode(OfflineStream s) { decode(ptr, s.ptr); }

    public OfflineRecognizerResult getResult(OfflineStream s) { return getResult(s.ptr); }

    public void release() { if (ptr != 0) { delete(ptr); ptr = 0; } }

    private native void delete(long ptr);
    private native long createStream(long ptr);
    private native long createStreamWithHotwords(long ptr, String hotwords);
    private native void setConfig(long ptr, OfflineRecognizerConfig config);
    private native long newFromAsset(android.content.res.AssetManager assetManager, OfflineRecognizerConfig config);
    private native long newFromFile(OfflineRecognizerConfig config);
    private native void decode(long ptr, long streamPtr);
    private native OfflineRecognizerResult getResult(long streamPtr);

    static { System.loadLibrary("sherpa-onnx-jni"); }
}
