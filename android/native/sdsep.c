/* JNI bridge to sherpa-onnx's C API for source separation (the shipped JNI library has no binding for it). */
#include <jni.h>
#include <stdint.h>
#include "c-api.h"

void *memset(void *d, int c, unsigned long n) { unsigned char *p = d; while (n--) *p++ = (unsigned char)c; return d; }
void *memcpy(void *d, const void *s, unsigned long n) { unsigned char *p = d; const unsigned char *q = s; while (n--) *p++ = *q++; return d; }

/* 5.9: the phone's AI accelerator (NNAPI) instead of the processor, when the user turns it on */
static const char *prov = "cpu";
JNIEXPORT void JNICALL Java_app_sesdefteri_Separator_accel(JNIEnv *env, jclass cl, jboolean on) { prov = on ? "nnapi" : "cpu"; }

JNIEXPORT jlong JNICALL Java_app_sesdefteri_Separator_create(JNIEnv *env, jclass cl, jstring voc, jstring acc, jint threads) {
  const char *v = (*env)->GetStringUTFChars(env, voc, 0);
  const char *a = (*env)->GetStringUTFChars(env, acc, 0);
  SherpaOnnxOfflineSourceSeparationConfig cfg;
  memset(&cfg, 0, sizeof cfg);
  if (a[0]) { cfg.model.spleeter.vocals = v; cfg.model.spleeter.accompaniment = a; cfg.model.uvr.model = ""; }
  else { cfg.model.spleeter.vocals = ""; cfg.model.spleeter.accompaniment = ""; cfg.model.uvr.model = v; }   /* 5.6: no accompaniment model = UVR */
  cfg.model.num_threads = threads;
  cfg.model.debug = 0;
  cfg.model.provider = prov;
  const SherpaOnnxOfflineSourceSeparation *p = SherpaOnnxCreateOfflineSourceSeparation(&cfg);
  (*env)->ReleaseStringUTFChars(env, voc, v);
  (*env)->ReleaseStringUTFChars(env, acc, a);
  return (jlong)(intptr_t)p;
}

JNIEXPORT void JNICALL Java_app_sesdefteri_Separator_destroy(JNIEnv *env, jclass cl, jlong h) {
  if (h) SherpaOnnxDestroyOfflineSourceSeparation((const SherpaOnnxOfflineSourceSeparation *)(intptr_t)h);
}

/* chans: float[channels][n]; returns float[stems*channels][m], output rate in outRate[0] */
JNIEXPORT jobjectArray JNICALL Java_app_sesdefteri_Separator_process(JNIEnv *env, jclass cl, jlong h, jobjectArray chans, jint rate, jintArray outRate) {
  int nc = (*env)->GetArrayLength(env, chans);
  if (nc < 1 || nc > 2 || !h) return 0;
  jfloatArray arr[2];
  float *ptr[2];
  int n = 0;
  for (int i = 0; i < nc; i++) {
    arr[i] = (jfloatArray)(*env)->GetObjectArrayElement(env, chans, i);
    n = (*env)->GetArrayLength(env, arr[i]);
    ptr[i] = (*env)->GetFloatArrayElements(env, arr[i], 0);
  }
  const SherpaOnnxSourceSeparationOutput *out = SherpaOnnxOfflineSourceSeparationProcess(
      (const SherpaOnnxOfflineSourceSeparation *)(intptr_t)h, (const float *const *)ptr, nc, n, rate);
  for (int i = 0; i < nc; i++) (*env)->ReleaseFloatArrayElements(env, arr[i], ptr[i], JNI_ABORT);
  if (!out) return 0;
  int total = 0;
  for (int s = 0; s < out->num_stems; s++) total += out->stems[s].num_channels;
  jclass fa = (*env)->FindClass(env, "[F");
  jobjectArray res = (*env)->NewObjectArray(env, total, fa, 0);
  int k = 0;
  for (int s = 0; s < out->num_stems && res; s++) {
    const SherpaOnnxSourceSeparationStem *st = &out->stems[s];
    for (int c = 0; c < st->num_channels; c++) {
      jfloatArray x = (*env)->NewFloatArray(env, st->n);
      if (!x) { res = 0; break; }
      (*env)->SetFloatArrayRegion(env, x, 0, st->n, st->samples[c]);
      (*env)->SetObjectArrayElement(env, res, k++, x);
      (*env)->DeleteLocalRef(env, x);
    }
  }
  jint r = out->sample_rate;
  (*env)->SetIntArrayRegion(env, outRate, 0, 1, &r);
  SherpaOnnxDestroySourceSeparationOutput(out);
  return res;
}

/* ---- 2.4: speech denoiser (DPDFNet, streaming) ---- */
JNIEXPORT jlong JNICALL Java_app_sesdefteri_Denoiser_create(JNIEnv *env, jclass cl, jstring model, jint threads) {
  const char *m = (*env)->GetStringUTFChars(env, model, 0);
  SherpaOnnxOnlineSpeechDenoiserConfig cfg;
  memset(&cfg, 0, sizeof cfg);
  cfg.model.gtcrn.model = "";
  cfg.model.dpdfnet.model = m;
  cfg.model.num_threads = threads;
  cfg.model.provider = prov;
  const SherpaOnnxOnlineSpeechDenoiser *p = SherpaOnnxCreateOnlineSpeechDenoiser(&cfg);
  (*env)->ReleaseStringUTFChars(env, model, m);
  return (jlong)(intptr_t)p;
}

JNIEXPORT void JNICALL Java_app_sesdefteri_Denoiser_destroy(JNIEnv *env, jclass cl, jlong h) {
  if (h) SherpaOnnxDestroyOnlineSpeechDenoiser((const SherpaOnnxOnlineSpeechDenoiser *)(intptr_t)h);
}

static jfloatArray den_out(JNIEnv *env, const SherpaOnnxDenoisedAudio *o) {
  if (!o) return (*env)->NewFloatArray(env, 0);
  jfloatArray x = (*env)->NewFloatArray(env, o->n);
  if (x && o->n > 0) (*env)->SetFloatArrayRegion(env, x, 0, o->n, o->samples);
  SherpaOnnxDestroyDenoisedAudio(o);
  return x;
}

/* feeds n samples (48 kHz mono); returns whatever is ready; n < 0 flushes the rest */
JNIEXPORT jfloatArray JNICALL Java_app_sesdefteri_Denoiser_run(JNIEnv *env, jclass cl, jlong h, jfloatArray in, jint n, jint rate) {
  const SherpaOnnxOnlineSpeechDenoiser *d = (const SherpaOnnxOnlineSpeechDenoiser *)(intptr_t)h;
  if (!d) return 0;
  if (n < 0) return den_out(env, SherpaOnnxOnlineSpeechDenoiserFlush(d));
  float *p = (*env)->GetFloatArrayElements(env, in, 0);
  const SherpaOnnxDenoisedAudio *o = SherpaOnnxOnlineSpeechDenoiserRun(d, p, n, rate);
  (*env)->ReleaseFloatArrayElements(env, in, p, JNI_ABORT);
  return den_out(env, o);
}

/* ---- 4.15: the same model on whole chunks (offline API): ~1.45x faster than streaming, and several instances can run side by side ---- */
JNIEXPORT jlong JNICALL Java_app_sesdefteri_Denoiser_createOff(JNIEnv *env, jclass cl, jstring model, jint threads) {
  const char *m = (*env)->GetStringUTFChars(env, model, 0);
  SherpaOnnxOfflineSpeechDenoiserConfig cfg;
  memset(&cfg, 0, sizeof cfg);
  cfg.model.gtcrn.model = "";
  cfg.model.dpdfnet.model = m;
  cfg.model.num_threads = threads;
  cfg.model.provider = prov;
  const SherpaOnnxOfflineSpeechDenoiser *p = SherpaOnnxCreateOfflineSpeechDenoiser(&cfg);
  (*env)->ReleaseStringUTFChars(env, model, m);
  return (jlong)(intptr_t)p;
}

JNIEXPORT void JNICALL Java_app_sesdefteri_Denoiser_destroyOff(JNIEnv *env, jclass cl, jlong h) {
  if (h) SherpaOnnxDestroyOfflineSpeechDenoiser((const SherpaOnnxOfflineSpeechDenoiser *)(intptr_t)h);
}

/* n samples (48 kHz mono) in, the same span out, aligned with the input */
JNIEXPORT jfloatArray JNICALL Java_app_sesdefteri_Denoiser_runOff(JNIEnv *env, jclass cl, jlong h, jfloatArray in, jint n, jint rate) {
  const SherpaOnnxOfflineSpeechDenoiser *d = (const SherpaOnnxOfflineSpeechDenoiser *)(intptr_t)h;
  if (!d || n <= 0) return 0;
  float *p = (*env)->GetFloatArrayElements(env, in, 0);
  const SherpaOnnxDenoisedAudio *o = SherpaOnnxOfflineSpeechDenoiserRun(d, p, n, rate);
  (*env)->ReleaseFloatArrayElements(env, in, p, JNI_ABORT);
  return den_out(env, o);
}

/* ---- 4.12: HD voice (LavaSR: backbone + spectrogram head) straight through ONNX Runtime's C API ---- */
#include "onnxruntime_c_api.h"
extern OrtStatus *OrtSessionOptionsAppendExecutionProvider_Nnapi(OrtSessionOptions *o, unsigned int flags);

static const OrtApi *ort;
static OrtEnv *hdEnv;
static OrtSession *hdB, *hdH;
static OrtMemoryInfo *hdMem;

static int ok(OrtStatus *s) { if (!s) return 1; ort->ReleaseStatus(s); return 0; }

static void hd_free(void) {
  if (!ort) return;
  if (hdB) ort->ReleaseSession(hdB);
  if (hdH) ort->ReleaseSession(hdH);
  if (hdMem) ort->ReleaseMemoryInfo(hdMem);
  if (hdEnv) ort->ReleaseEnv(hdEnv);
  hdB = hdH = 0; hdMem = 0; hdEnv = 0;
}

JNIEXPORT void JNICALL Java_app_sesdefteri_Hd_close(JNIEnv *env, jclass cl) { hd_free(); }

JNIEXPORT jboolean JNICALL Java_app_sesdefteri_Hd_open(JNIEnv *env, jclass cl, jstring backbone, jstring head, jint threads) {
  hd_free();
  if (!ort) ort = OrtGetApiBase()->GetApi(16);   /* everything used here exists since 1.16 */
  if (!ort) return 0;
  const char *b = (*env)->GetStringUTFChars(env, backbone, 0), *h = (*env)->GetStringUTFChars(env, head, 0);
  OrtSessionOptions *o = 0;
  int r = ok(ort->CreateEnv(ORT_LOGGING_LEVEL_ERROR, "sd", &hdEnv)) && ok(ort->CreateSessionOptions(&o))
       && ok(ort->SetIntraOpNumThreads(o, threads)) && ok(ort->SetSessionGraphOptimizationLevel(o, ORT_ENABLE_ALL))
       && (prov[0] != 'n' || ok(OrtSessionOptionsAppendExecutionProvider_Nnapi(o, 0)) || 1)
       && ok(ort->CreateSession(hdEnv, b, o, &hdB)) && ok(ort->CreateSession(hdEnv, h, o, &hdH))
       && ok(ort->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &hdMem));
  if (o) ort->ReleaseSessionOptions(o);
  (*env)->ReleaseStringUTFChars(env, backbone, b);
  (*env)->ReleaseStringUTFChars(env, head, h);
  if (!r) hd_free();
  return r;
}

/* mel: float[80*frames] ([80][frames]); returns float[2*1025*frames]: real [1025][frames], then imaginary */
JNIEXPORT jfloatArray JNICALL Java_app_sesdefteri_Hd_run(JNIEnv *env, jclass cl, jfloatArray mel, jint frames) {
  if (!hdB || !hdH || frames < 1) return 0;
  int64_t shape[3] = { 1, 80, frames };
  float *m = (*env)->GetFloatArrayElements(env, mel, 0);
  OrtValue *in = 0, *hid = 0, *out[2] = { 0, 0 };
  const char *nMel[] = { "mel" }, *nHid[] = { "hidden" }, *nOut[] = { "real", "imag" };
  jfloatArray res = 0;
  if (ok(ort->CreateTensorWithDataAsOrtValue(hdMem, m, (size_t)80 * frames * 4, shape, 3, ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &in))
      && ok(ort->Run(hdB, 0, nMel, (const OrtValue *const *)&in, 1, nHid, 1, &hid))
      && ok(ort->Run(hdH, 0, nHid, (const OrtValue *const *)&hid, 1, nOut, 2, out))) {
    float *re = 0, *im = 0;
    int n = 1025 * frames;
    if (ok(ort->GetTensorMutableData(out[0], (void **)&re)) && ok(ort->GetTensorMutableData(out[1], (void **)&im))) {
      res = (*env)->NewFloatArray(env, 2 * n);
      if (res) { (*env)->SetFloatArrayRegion(env, res, 0, n, re); (*env)->SetFloatArrayRegion(env, res, n, n, im); }
    }
  }
  if (in) ort->ReleaseValue(in);
  if (hid) ort->ReleaseValue(hid);
  if (out[0]) ort->ReleaseValue(out[0]);
  if (out[1]) ort->ReleaseValue(out[1]);
  (*env)->ReleaseFloatArrayElements(env, mel, m, JNI_ABORT);
  return res;
}
