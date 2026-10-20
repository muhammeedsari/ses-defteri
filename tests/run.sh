#!/bin/bash
# compile the app's pure-Java DSP + tests and run one test class
set -e
SRC=/home/claude/ses-defteri-android/src/app/sesdefteri/dsp
OUT=/home/claude/sd-tests/classes
rm -rf $OUT; mkdir -p $OUT
javac -nowarn -source 8 -target 8 -Xlint:-options -d $OUT $SRC/*.java /home/claude/sd-tests/java/*.java 2>&1 | grep -v "JAVA_TOOL_OPTIONS" || true
cls=$1; shift
java -Xmx3g -cp $OUT $cls "$@" 2>&1 | grep -v JAVA_TOOL_OPTIONS
