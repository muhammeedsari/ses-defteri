#!/bin/bash
# compile app pure-Java parts (dsp, asr, sherpa mirrors) + tests, run with the desktop JNI build of sherpa-onnx
set -e
A=/home/claude/ses-defteri-android/src
OUT=/home/claude/sd-tests/classes2
rm -rf $OUT; mkdir -p $OUT
javac -nowarn -source 8 -target 8 -Xlint:-options -encoding UTF-8 -cp /home/claude/sdk/android-34.jar -d $OUT $A/app/sesdefteri/dsp/*.java $A/app/sesdefteri/asr/*.java $A/com/k2fsa/sherpa/onnx/*.java /home/claude/sd-tests/java/*.java 2>&1 | grep -v "JAVA_TOOL_OPTIONS" || true
cls=$1; shift
LD_LIBRARY_PATH=/home/claude/vendor/sherpa-onnx-v1.13.8-linux-x64-jni/lib java -Xmx4g -Djava.library.path=/home/claude/vendor/sherpa-onnx-v1.13.8-linux-x64-jni/lib -cp $OUT $cls "$@" 2>&1 | grep -v JAVA_TOOL_OPTIONS
