#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../app/src/main/cpp"
# Pinned release; checkout is required before Gradle/CMake configuration.
if [ ! -d llama.cpp/.git ]; then
  git clone --depth 1 --branch b8182 https://github.com/ggml-org/llama.cpp.git llama.cpp
fi
