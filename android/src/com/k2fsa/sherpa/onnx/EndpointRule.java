package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class EndpointRule {
    public boolean mustContainNonSilence;
    public float minTrailingSilence;
    public float minUtteranceLength;
    public EndpointRule(boolean m, float t, float u) { mustContainNonSilence = m; minTrailingSilence = t; minUtteranceLength = u; }
}
