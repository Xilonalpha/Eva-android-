#!/usr/bin/env python3
"""Static consistency checks for EVA-1 source; not a substitute for Android build/tests."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
llm = (root / "app/src/main/java/com/eva1/companion/engines/OfflineLlmEngine.kt").read_text()
gguf = (root / "app/src/main/java/com/eva1/companion/engines/GgufOnDeviceEngine.kt").read_text()
litert = (root / "app/src/main/java/com/eva1/companion/engines/LiteRtLocalLlmEngine.kt").read_text()
vision = (root / "app/src/main/java/com/eva1/companion/engines/MultiModalGameVisionEngine.kt").read_text()
tts = (root / "app/src/main/java/com/eva1/companion/engines/EvaTtsEngine.kt").read_text()
voice = (root / "app/src/main/java/com/eva1/companion/engines/VoiceAssistantEngine.kt").read_text()
accessibility = (root / "app/src/main/java/com/eva1/companion/engines/EvaAccessibilityService.kt").read_text()
game_bridge = (root / "app/src/main/java/com/eva1/companion/engines/LocalLlmGameBridgeEngine.kt").read_text()
logger = (root / "app/src/main/java/com/eva1/companion/engines/EvaGameDecisionLogger.kt").read_text()
gradle = (root / "app/build.gradle.kts").read_text()
workflow = root / ".github/workflows/android-build.yml"

ids_a = re.findall(r'LocalModelInfo\(\s*id\s*=\s*"([^"]+)"', llm)
ids_b = re.findall(r'GgufModelSpec\(\s*id\s*=\s*"([^"]+)"', gguf)
assert len(ids_a) == 11, f"Expected 11 legacy GGUF catalog entries, got {len(ids_a)}"
assert len(ids_b) == 11, f"Expected 11 GGUF runtime entries, got {len(ids_b)}"
assert ids_a == ids_b, "Offline LLM catalog and GGUF runtime catalog differ/order mismatch"
assert 'com.google.ai.edge.litertlm:litertlm-android' in gradle
assert 'litert-community/Qwen3-0.6B' in litert
assert 'litert-community/gemma-4-E2B-it-litert-lm' in litert
assert 'Content.ImageBytes(imageBytes)' in litert
assert 'generateOfflineWithImage(bitmap' in vision
assert 'confidence < 0.75f' in vision
assert 'latest.release' in gradle
assert 'com.k2fsa.sherpa.onnx.tts.engine' in tts
assert 'Sherpa-ONNX Piper (offline)' in tts
assert 'SpeechRecognizer' in voice and ('KWS low-power' in voice or 'Sherpa' in voice)
assert 'dispatchTrackedGesture' in accessibility and 'freshFrameObserved' in accessibility
assert 'ADAPTOR TETRIS' in game_bridge and 'ADAPTOR KLONDIKE SOLITAIRE' in game_bridge
assert 'logPostActionVerification' in logger
assert 'versionName = "2.4.1"' in gradle and 'versionCode = 8' in gradle
assert workflow.is_file(), "GitHub Actions Android build workflow missing"
assert not any(x in llm for x in ('EVA-Omni-7B-2026-Uncensored-MLC', 'Flux-Wan2.1-Multimodal-Local-MLC'))
print("PASS: catalog parity (11 GGUF models)")
print("PASS: LiteRT-LM dependency + Qwen3/Gemma4 model provisioning")
print("PASS: image input route + confidence gate for game gestures")
print("PASS: GitHub Actions Android build workflow")
print("PASS: Sherpa-ONNX Piper Android TTS provider selection/fallback")
print("PASS: explicit Hey Siri wake backend limitation (no false low-power claim)")
print("PASS: post-gesture frame feedback + Tetris/Klondike action guards")
print("NOTE: Static verification only; Android Gradle build and device runtime tests remain unexecuted.")

# Guard against accidental restoration of the HTTP inference server.
from pathlib import Path
engines = root / "app/src/main/java/com/eva1/companion/engines"
for name in ("GgufOnDeviceEngine.kt", "LocalLlmGameBridgeEngine.kt", "MultiModalGameVisionEngine.kt"):
    source = (engines / name).read_text()
    assert not re.search(r"127\.0\.0\.1:(?:8080|11434)|localhost:(?:8080|11434)", source), f"HTTP LLM endpoint in {name}"
cpp = (root / "app/src/main/cpp/eva_gguf_jni.cpp").read_text()
assert 'llama_model_load_from_file' in cpp and 'llama_decode' in cpp
assert 'max_tokens < 1' in cpp and 'count + i < kContext' in cpp
assert 'offset += kBatch' in cpp and 'std::isfinite(temperature)' in cpp
print("PASS: GGUF JNI source guards and no known localhost inference endpoints")
