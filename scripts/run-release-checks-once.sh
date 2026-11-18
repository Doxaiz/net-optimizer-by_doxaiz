#!/usr/bin/env bash
set -euo pipefail
adb shell wm size 1080x2400
adb shell wm density 420
if ! python3 scripts/check-release-upgrade-emulator.py published/app-release.apk candidate/app-release.apk > /tmp/upgrade-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/upgrade-check.log').read_text(errors='replace')[-2500:]; print('::error::Upgrade check: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
if ! python3 scripts/check-data-saver-emulator.py candidate/app-release.apk > /tmp/data-saver-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/data-saver-check.log').read_text(errors='replace')[-2500:]; print('::error::Data Saver check: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
cat /tmp/upgrade-check.log /tmp/data-saver-check.log
