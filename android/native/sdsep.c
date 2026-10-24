/* JNI bridge to sherpa-onnx's C API for source separation (the shipped JNI library has no binding for it). */
#include <jni.h>
#include <stdint.h>
#include "c-api.h"

void *memset(void *d, int c, unsigned long n) { unsigned char *p = d; while (n--) *p++ = (unsigned char)c; return d; }
void *memcpy(void *d, const void *s, unsigned long n) { unsigned char *p = d; const unsigned char *q = s; while (n--) *p++ = *q++; return d; }

JNIEXPORT jlong JNICALL Java_app_sesdefteri_Separator_create(JNIEnv *env, jclass cl, jstring voc, jstring acc, jint threads) {
  const char *v = (*env)->GetStringUTFChars(env, voc, 0);
  const char *a = (*env)->GetStringUTFChars(env, acc, 0);
  SherpaOnnxOfflineSourceSeparationConfig cfg;
  memset(&cfg, 0, sizeof cfg);
  cfg.model.spleeter.vocals = v;
  cfg.model.spleeter.accompaniment = a;
  cfg.model.uvr.model = "";
  cfg.model.num_threads = threads;
  cfg.model.debug = 0;
  cfg.model.provider = "cpu";
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
  cfg.model.provider = "cpu";
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
