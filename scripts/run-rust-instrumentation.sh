#!/usr/bin/env bash
# Only run on a disposable emulator. These APKs use the debug signing identity.
set -euo pipefail
# A successful install alone does not establish the emulator's actual page size.
if [[ -n ${2:-} ]]; then
  actual_page_size=$(adb shell getconf PAGE_SIZE | tr -d '\r\n')
  echo "Android page size: $actual_page_size bytes (expected $2)"
  if [[ "$actual_page_size" != "$2" ]]; then
    echo '::error::Emulator page size does not match the requested test image' >&2
    exit 1
  fi
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
log="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/netoptimizer-rust-instrumentation.log"
test_class=${1:-com.doxaiz.netoptimizer.core.net.NativeMeasurementsInstrumentedTest}
status=0
adb shell am instrument -w -r -e class \
  "$test_class" \
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
