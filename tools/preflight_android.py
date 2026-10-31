#!/usr/bin/env python3
"""Offline, dependency-free checks before an Android build (not a compiler)."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest = root / 'app/src/main/AndroidManifest.xml'
android = '{http://schemas.android.com/apk/res/android}'
errors = []
try:
    tree = ET.parse(manifest)
    node = tree.getroot()
    permissions = {e.get(android+'name') for e in node.findall('uses-permission')}
    for p in ('android.permission.RECORD_AUDIO', 'android.permission.FOREGROUND_SERVICE_MICROPHONE',
              'android.permission.READ_CONTACTS', 'android.permission.INTERNET'):
        if p not in permissions:
            errors.append('Missing permission: '+p)
    app = node.find('application')
    services = {s.get(android+'name'): s for s in app.findall('service')}
    voice = services.get('com.eva1.companion.engines.EvaWakeWordForegroundService')
    if voice is None or 'microphone' not in (voice.get(android+'foregroundServiceType') or '').split('|'):
        errors.append('Microphone foreground service missing or wrong type')
    accessibility = services.get('com.eva1.companion.engines.EvaAccessibilityService')
    if accessibility is None or accessibility.get(android+'permission') != 'android.permission.BIND_ACCESSIBILITY_SERVICE':
        errors.append('Accessibility service must require BIND_ACCESSIBILITY_SERVICE')
except ET.ParseError as exc:
    errors.append('Manifest XML invalid: '+str(exc))

source_dir = root/'app/src/main/java'
kt_files = list(source_dir.rglob('*.kt'))
if not kt_files:
    errors.append('No Kotlin sources')
for f in kt_files:
    content = f.read_text(encoding='utf-8')
    if '\x00' in content:
        errors.append('NUL byte in '+str(f.relative_to(root)))

for f in ('app/src/main/cpp/CMakeLists.txt', 'app/src/main/cpp/eva_gguf_jni.cpp',
          'tools/prepare_sherpa_kws.sh', 'tools/prepare_llama_cpp.sh',
          '.github/workflows/android-build.yml'):
    if not (root/f).is_file():
        errors.append('Missing build input: '+f)

print(f'Android preflight: {len(kt_files)} Kotlin files, manifest parsed')
if errors:
    for e in errors:
        print('FAIL:',e,file=sys.stderr)
    sys.exit(1)
print('PASS: Android source/manifest preflight (not a build)')
