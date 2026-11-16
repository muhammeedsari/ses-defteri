#!/bin/bash
# compile app pure-Java parts (dsp, asr, sherpa mirrors) + tests, run with the desktop JNI build of sherpa-onnx
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
A="$ROOT/android/src"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
ANDROID_JAR="${SD_ANDROID_JAR:-$ROOT/vendor/android-34.jar}"
JNI="${SD_DESKTOP_JNI:?Set SD_DESKTOP_JNI to the desktop sherpa-onnx library directory}"
javac -nowarn -source 8 -target 8 -Xlint:-options -encoding UTF-8 -cp "$ANDROID_JAR" -d "$OUT" "$A"/app/sesdefteri/dsp/*.java "$A"/app/sesdefteri/asr/*.java "$A"/com/k2fsa/sherpa/onnx/*.java "$ROOT"/tests/java/*.java
cls=$1; shift
LD_LIBRARY_PATH="$JNI${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" java -Xmx4g -Djava.library.path="$JNI" -cp "$OUT:$ANDROID_JAR" "$cls" "$@"
