#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/src/main"
JAVA="$APP/java/com/k2fsa/sherpa/onnx"
ASSETS="$APP/assets"
JNI="$APP/jniLibs/arm64-v8a"
SHERPA_VERSION="2.0.1"
MODEL_DIR="sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
WORK="$ROOT/.sherpa-kws-build"

mkdir -p "$JAVA" "$ASSETS" "$JNI" "$WORK"

# Pin upstream API and native runtime to the same Sherpa release.
if [[ ! -f "$JAVA/KeywordSpotter.kt" ]]; then
  curl -fL --retry 3 "https://github.com/k2-fsa/sherpa-onnx/archive/refs/tags/v${SHERPA_VERSION}.tar.gz" -o "$WORK/sherpa-src.tar.gz"
  mkdir -p "$WORK/src"
  tar -xzf "$WORK/sherpa-src.tar.gz" -C "$WORK/src"
  SRC="$WORK/src/sherpa-onnx-${SHERPA_VERSION}/sherpa-onnx/kotlin-api"
  test -f "$SRC/KeywordSpotter.kt"
  cp "$SRC"/*.kt "$JAVA/"
fi

if [[ ! -f "$JNI/libsherpa-onnx-jni.so" || ! -f "$JNI/libonnxruntime.so" ]]; then
  curl -fL --retry 3 "https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-v${SHERPA_VERSION}-android.tar.bz2" -o "$WORK/sherpa-android.tar.bz2"
  mkdir -p "$WORK/android"
  tar -xjf "$WORK/sherpa-android.tar.bz2" -C "$WORK/android"
  SHERPA_LIB="$(find "$WORK/android" -type f -name 'libsherpa-onnx-jni.so' | grep -E 'arm64-v8a|arm64' | head -n 1 || true)"
  ORT_LIB="$(find "$WORK/android" -type f -name 'libonnxruntime.so' | grep -E 'arm64-v8a|arm64' | head -n 1 || true)"
  test -n "$SHERPA_LIB" && test -n "$ORT_LIB" || {
    echo "Nu am găsit bibliotecile native Sherpa-ONNX pentru ABI arm64-v8a în arhiva upstream." >&2
    exit 1
  }
  cp "$SHERPA_LIB" "$JNI/"
  cp "$ORT_LIB" "$JNI/"
fi

if [[ ! -f "$ASSETS/$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.onnx" ]]; then
  curl -fL --retry 3 "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/${MODEL_DIR}.tar.bz2" -o "$WORK/kws-model.tar.bz2"
  mkdir -p "$WORK/model"
  tar -xjf "$WORK/kws-model.tar.bz2" -C "$WORK/model"
  test -f "$WORK/model/$MODEL_DIR/tokens.txt"
  mkdir -p "$ASSETS/$MODEL_DIR"
  cp "$WORK/model/$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.onnx" "$ASSETS/$MODEL_DIR/"
  cp "$WORK/model/$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx" "$ASSETS/$MODEL_DIR/"
  cp "$WORK/model/$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.onnx" "$ASSETS/$MODEL_DIR/"
  cp "$WORK/model/$MODEL_DIR/tokens.txt" "$ASSETS/$MODEL_DIR/"
  if [[ -f "$WORK/model/$MODEL_DIR/keywords.txt" ]]; then
    cp "$WORK/model/$MODEL_DIR/keywords.txt" "$ASSETS/$MODEL_DIR/"
  else
    : > "$ASSETS/$MODEL_DIR/keywords.txt"
  fi
fi

# The English GigaSpeech KWS model accepts user-defined phrases through createStream().
# Verify all runtime assets before Gradle is allowed to compile the app.
test -s "$JAVA/KeywordSpotter.kt"
test -s "$JNI/libsherpa-onnx-jni.so"
test -s "$JNI/libonnxruntime.so"
test -s "$ASSETS/$MODEL_DIR/tokens.txt"
test -f "$ASSETS/$MODEL_DIR/keywords.txt"
test -s "$ASSETS/$MODEL_DIR/encoder-epoch-12-avg-2-chunk-16-left-64.onnx"
test -s "$ASSETS/$MODEL_DIR/decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
test -s "$ASSETS/$MODEL_DIR/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
echo "Sherpa-ONNX KWS runtime, Kotlin API and English wake-word model are ready."
