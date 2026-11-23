#!/usr/bin/env bash
# Only run on a disposable emulator. These APKs use the debug signing identity.
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
log="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/netoptimizer-rust-instrumentation.log"
status=0
adb shell am instrument -w -r -e class \
  com.doxaiz.netoptimizer.core.net.NativeMeasurementsInstrumentedTest \
  com.doxaiz.netoptimizer.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1 || status=$?
tr -d '\r' < "$log" > "$log.clean"
cat "$log.clean"
if [[ $status -ne 0 ]] || ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log.clean"; then
  python3 - "$log.clean" <<'PY'
from pathlib import Path
import sys
s = Path(sys.argv[1]).read_text(errors='replace')[-6000:]
print('::error::Android Rust instrumentation failed: ' + s.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PY
  exit 1
fi
