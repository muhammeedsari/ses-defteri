/* 5.10: JNI bridge to Signalsmith Stretch (MIT) for pitch shifting (app.sesdefteri.dsp.Stretch).
 * Built like libsdmp3.so: zig c++ for aarch64-linux-musl, -fno-exceptions -fno-rtti, linked against stub libc.so/libm.so
 * so Android's own libc/libm are used at run time. The few C++ runtime pieces it needs are defined below. */
#include <jni.h>
#include "signalsmith-stretch.h"
#include <cstdlib>
#include <random>

typedef signalsmith::stretch::SignalsmithStretch<float> St;

struct H { St s; int ch; float *buf[2]; int cap; };

static void room(H *h, int n) {
  if (n <= h->cap) return;
  for (int c = 0; c < h->ch; c++) { free(h->buf[c]); h->buf[c] = (float *) malloc(sizeof(float) * n * 2); }
  h->cap = n * 2;
}
static void rd(JNIEnv *e, H *h, jobjectArray a, int n, float **dst) {
  for (int c = 0; c < h->ch; c++) { jfloatArray x = (jfloatArray) e->GetObjectArrayElement(a, c); e->GetFloatArrayRegion(x, 0, n, dst[c]); e->DeleteLocalRef(x); }
}
static void wr(JNIEnv *e, H *h, jobjectArray a, int n, float **src) {
  for (int c = 0; c < h->ch; c++) { jfloatArray x = (jfloatArray) e->GetObjectArrayElement(a, c); e->SetFloatArrayRegion(x, 0, n, src[c]); e->DeleteLocalRef(x); }
}

extern "C" {
JNIEXPORT jlong JNICALL Java_app_sesdefteri_dsp_Stretch_create(JNIEnv *e, jclass, jint ch, jfloat rate, jfloat semis, jboolean formant) {
  H *h = new H();
  h->ch = ch < 1 ? 1 : ch > 2 ? 2 : ch; h->cap = 0; h->buf[0] = h->buf[1] = 0;
  h->s.presetDefault(h->ch, rate);
  h->s.setTransposeSemitones(semis, 8000 / rate);
  if (formant) h->s.setFormantSemitones(0, true);
  return (jlong) h;
}
JNIEXPORT void JNICALL Java_app_sesdefteri_dsp_Stretch_destroy(JNIEnv *, jclass, jlong p) {
  H *h = (H *) p; if (!h) return;
  free(h->buf[0]); free(h->buf[1]); delete h;
}
JNIEXPORT jint JNICALL Java_app_sesdefteri_dsp_Stretch_seekLength(JNIEnv *, jclass, jlong p, jfloat rate) { return ((H *) p)->s.outputSeekLength(rate); }
JNIEXPORT void JNICALL Java_app_sesdefteri_dsp_Stretch_seek(JNIEnv *e, jclass, jlong p, jobjectArray in, jint n) {
  H *h = (H *) p; room(h, n); rd(e, h, in, n, h->buf); h->s.outputSeek(h->buf, n);
}
/* n input samples in, m output samples out (both arrays at least that long) */
JNIEXPORT void JNICALL Java_app_sesdefteri_dsp_Stretch_process(JNIEnv *e, jclass, jlong p, jobjectArray in, jint n, jobjectArray out, jint m) {
  H *h = (H *) p; room(h, n > m ? n : m);
  float *o[2]; o[0] = (float *) malloc(sizeof(float) * (m + 1)); o[1] = h->ch > 1 ? (float *) malloc(sizeof(float) * (m + 1)) : 0;
  rd(e, h, in, n, h->buf);
  h->s.process(h->buf, n, o, m);
  wr(e, h, out, m, o);
  free(o[0]); free(o[1]);
}
}

/* the C++ runtime pieces the header pulls in (only for the phone build, which has no C++ library) */
#ifdef _LIBCPP_VERSION
void *operator new(size_t n) { return malloc(n); }
void *operator new[](size_t n) { return malloc(n); }
void operator delete(void *p) noexcept { free(p); }
void operator delete(void *p, size_t) noexcept { free(p); }
void operator delete[](void *p) noexcept { free(p); }
void operator delete[](void *p, size_t) noexcept { free(p); }
namespace std { inline namespace __1 {
random_device::random_device(const string &) { }
random_device::~random_device() { }
unsigned random_device::operator()() { return 0x5d0e1u; }
[[noreturn]] void __libcpp_verbose_abort(const char *, ...) { abort(); }
} }
#endif
