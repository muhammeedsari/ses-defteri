package com.k2fsa.sherpa.onnx;

/** Mirror of the sherpa-onnx Kotlin data class (field names and types are read by the native library). */
public class OnlineModelConfig {
    public OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
    public OnlineParaformerModelConfig paraformer = new OnlineParaformerModelConfig();
    public OnlineZipformer2CtcModelConfig zipformer2Ctc = new OnlineZipformer2CtcModelConfig();
    public OnlineNeMoCtcModelConfig neMoCtc = new OnlineNeMoCtcModelConfig();
    public OnlineToneCtcModelConfig toneCtc = new OnlineToneCtcModelConfig();
    public String tokens = "";
    public int numThreads = 1;
    public boolean debug = false;
    public String provider = "cpu";
    public String modelType = "";
    public String modelingUnit = "";
    public String bpeVocab = "";
}
