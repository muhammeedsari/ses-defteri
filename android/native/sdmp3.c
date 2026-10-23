/* JNI bridge to LAME (libmp3lame, LGPL) for MP3 export. */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "lame.h"

static void quiet(const char *fmt, va_list ap) { (void)fmt; (void)ap; }

JNIEXPORT jlong JNICALL Java_app_sesdefteri_Mp3_init(JNIEnv *env, jclass cl, jint rate, jint ch, jint kbps) {
  lame_global_flags *g = lame_init();
  if (!g) return 0;
  lame_set_errorf(g, quiet); lame_set_debugf(g, quiet); lame_set_msgf(g, quiet);
  lame_set_in_samplerate(g, rate);
  lame_set_out_samplerate(g, rate);
  lame_set_num_channels(g, ch);
  lame_set_mode(g, ch == 1 ? MONO : JOINT_STEREO);
  lame_set_brate(g, kbps);
  lame_set_quality(g, 5);
  lame_set_bWriteVbrTag(g, 0);
  if (lame_init_params(g) < 0) { lame_close(g); return 0; }
  return (jlong)(intptr_t)g;
}

/* pcm: interleaved 16-bit, frames = samples per channel; returns the MP3 bytes produced */
JNIEXPORT jbyteArray JNICALL Java_app_sesdefteri_Mp3_encode(JNIEnv *env, jclass cl, jlong h, jshortArray pcm, jint frames, jint ch) {
  lame_global_flags *g = (lame_global_flags *)(intptr_t)h;
  int cap = frames * 5 / 4 + 7200;
  unsigned char *out = (unsigned char *)malloc(cap);
  if (!out) return 0;
  jshort *p = (*env)->GetShortArrayElements(env, pcm, 0);
  int n = ch == 1 ? lame_encode_buffer(g, p, p, frames, out, cap) : lame_encode_buffer_interleaved(g, p, frames, out, cap);
  (*env)->ReleaseShortArrayElements(env, pcm, p, JNI_ABORT);
  jbyteArray r = 0;
  if (n >= 0) { r = (*env)->NewByteArray(env, n); if (r && n) (*env)->SetByteArrayRegion(env, r, 0, n, (const jbyte *)out); }
  free(out);
  return r;
}

JNIEXPORT jbyteArray JNICALL Java_app_sesdefteri_Mp3_flush(JNIEnv *env, jclass cl, jlong h) {
  lame_global_flags *g = (lame_global_flags *)(intptr_t)h;
  unsigned char out[7200];
  int n = lame_encode_flush(g, out, sizeof out);
  if (n < 0) n = 0;
  jbyteArray r = (*env)->NewByteArray(env, n);
  if (r && n) (*env)->SetByteArrayRegion(env, r, 0, n, (const jbyte *)out);
  return r;
}

JNIEXPORT void JNICALL Java_app_sesdefteri_Mp3_close(JNIEnv *env, jclass cl, jlong h) {
  if (h) lame_close((lame_global_flags *)(intptr_t)h);
}

/* the compiler may turn memcmp(...) == 0 into bcmp, which older Android libc versions don't export */
int bcmp(const void *a, const void *b, size_t n) { const unsigned char *p = a, *q = b; while (n--) if (*p++ != *q++) return 1; return 0; }
