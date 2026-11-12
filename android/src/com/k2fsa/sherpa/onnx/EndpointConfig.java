package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class EndpointConfig {
    public EndpointRule rule1 = new EndpointRule(false, 2.4f, 0.0f);
    public EndpointRule rule2 = new EndpointRule(true, 1.4f, 0.0f);
    public EndpointRule rule3 = new EndpointRule(false, 0.0f, 20.0f);
}
