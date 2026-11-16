#!/bin/bash
# compile the app's pure-Java DSP + tests and run one test class
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/android/src/app/sesdefteri/dsp"
OUT="$(mktemp -d)"
trap 'rm -rf -- "$OUT"' EXIT
javac -nowarn -source 8 -target 8 -Xlint:-options -encoding UTF-8 -d "$OUT" "$SRC"/*.java "$ROOT"/tests/java/*.java
cls=$1; shift
java -Xmx3g -cp "$OUT" "$cls" "$@"
