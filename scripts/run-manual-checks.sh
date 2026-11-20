#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1080x2400
adb shell wm density 420
export AUDIT_DISPOSABLE_EMULATOR=yes
python3 scripts/check-manual-optimization-emulator.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/check-manual-app-emulator.py app/build/outputs/apk/debug/app-debug.apk "$RUNNER_TEMP/netopt-fixture/fixture.apk"
python3 scripts/check-mobile-load-emulator.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/check-navigation-emulator.py app/build/outputs/apk/debug/app-debug.apk
