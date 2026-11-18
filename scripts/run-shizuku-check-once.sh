#!/usr/bin/env bash
set -euo pipefail
if ! AUDIT_DISPOSABLE_EMULATOR=yes python3 scripts/check-shizuku-emulator.py app/build/outputs/apk/debug/app-debug.apk /tmp/shizuku.apk > /tmp/shizuku-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/shizuku-check.log').read_text(errors='replace')[-6000:]; print('::error::Shizuku integration: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
cat /tmp/shizuku-check.log
