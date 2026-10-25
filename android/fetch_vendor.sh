#!/usr/bin/env bash
# Downloads everything the APK needs that is not in git into ../vendor (≈ 200 MB of downloads).
# Needs: curl, tar (bzip2), npm. Tools for build_apk.py (Ubuntu/Debian): apt install aapt zipalign apksigner dalvik-exchange openjdk-21-jdk python3-pil
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
V="${SD_VENDOR:-$ROOT/vendor}"
GH=https://github.com/k2-fsa/sherpa-onnx/releases/download
mkdir -p "$V"/{jniLibs/arm64-v8a,jniLibs/armeabi-v7a,sepm,dn,dia,asr,fonts,tmp}
cd "$V/tmp"

# 1) sherpa-onnx v1.13.8 Android libraries (ONNX Runtime + JNI + C API)
[ -f android.tar.bz2 ] || curl -fL -o android.tar.bz2 "$GH/v1.13.8/sherpa-onnx-v1.13.8-android.tar.bz2"
tar xjf android.tar.bz2
for abi in arm64-v8a armeabi-v7a; do
  for so in libsherpa-onnx-jni.so libonnxruntime.so libsherpa-onnx-c-api.so; do cp "jniLibs/$abi/$so" "$V/jniLibs/$abi/"; done
done
# our own small JNI bridges (source in android/native, prebuilt so no cross compiler is needed)
cp "$ROOT"/android/native/prebuilt/arm64-v8a/*.so "$V/jniLibs/arm64-v8a/"
cp "$ROOT"/android/native/prebuilt/armeabi-v7a/*.so "$V/jniLibs/armeabi-v7a/"

# 2) voice separation (Spleeter 2 stems, fp16)
[ -f spleeter.tar.bz2 ] || curl -fL -o spleeter.tar.bz2 "$GH/source-separation-models/sherpa-onnx-spleeter-2stems-fp16.tar.bz2"
tar xjf spleeter.tar.bz2
cp "$(find . -name 'vocals.fp16.onnx' | head -1)" "$(find . -name 'accompaniment.fp16.onnx' | head -1)" "$V/sepm/"

# 3) AI speech denoiser (DPDFNet-2, 48 kHz)
[ -f "$V/dn/dpdfnet2_48khz_hr.onnx" ] || curl -fL -o "$V/dn/dpdfnet2_48khz_hr.onnx" "$GH/speech-enhancement-models/dpdfnet2_48khz_hr.onnx"

# 3b) speaker segmentation (pyannote 3.0, int8) for speaker separation
if [ ! -f "$V/dia/pyannote.int8.onnx" ]; then
  curl -fL -o seg.tar.bz2 "$GH/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
  tar xjf seg.tar.bz2
  cp "$(find . -name 'model.int8.onnx' -path '*pyannote*' | head -1)" "$V/dia/pyannote.int8.onnx"
fi

# 4) voice activity detector for speech-to-text (Whisper itself is downloaded inside the app)
[ -f "$V/asr/silero_vad.onnx" ] || curl -fL -o "$V/asr/silero_vad.onnx" "$GH/asr-models/silero_vad.onnx"

# 5) fonts (offline in the app) and the WebM duration fix used by the web recorder
for f in ibm-plex-sans ibm-plex-mono bricolage-grotesque; do
  d="$V/fonts/fontsource-$f-5.3.0"
  if [ ! -d "$d/package" ]; then t=$(npm pack "@fontsource/$f@5.3.0" --silent); mkdir -p "$d"; tar xzf "$t" -C "$d"; fi
done
if [ ! -f "$V/fix-webm-duration.js" ]; then t=$(npm pack fix-webm-duration@1.0.6 --silent); tar xzf "$t"; cp package/fix-webm-duration.js "$V/"; rm -rf package; fi

cd "$V" && rm -rf tmp
if [ ! -f "$V/android-34.jar" ]; then
  echo "NOTE: put an Android API 34 android.jar at $V/android-34.jar (Android SDK: platforms/android-34/android.jar)"
fi
echo "vendor ready in $V"
