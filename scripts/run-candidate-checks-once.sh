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
if ! python3 scripts/check-manual-optimization-emulator.py candidate/app-release.apk > /tmp/manual-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/manual-check.log').read_text(errors='replace')[-4000:]; print('::error::Manual Data Saver check: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
if ! python3 scripts/check-manual-app-emulator.py candidate/app-release.apk "$RUNNER_TEMP/netopt-fixture/fixture.apk" > /tmp/manual-app-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/manual-app-check.log').read_text(errors='replace')[-4000:]; print('::error::Manual app check: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
if ! python3 scripts/check-shizuku-emulator.py candidate/app-release.apk /tmp/shizuku.apk > /tmp/shizuku-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/shizuku-check.log').read_text(errors='replace')[-6000:]; print('::error::Shizuku check: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
if ! python3 scripts/check-mobile-load-emulator.py candidate/app-release.apk > /tmp/mobile-load-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/mobile-load-check.log').read_text(errors='replace')[-4000:]; print('::error::Mobile load check: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
if ! python3 scripts/check-navigation-emulator.py candidate/app-release.apk > /tmp/navigation-check.log 2>&1; then
  python3 -c "from pathlib import Path; s=Path('/tmp/navigation-check.log').read_text(errors='replace')[-4000:]; print('::error::Navigation check: ' + s.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))"
  exit 1
fi
cat /tmp/navigation-check.log /tmp/mobile-load-check.log /tmp/upgrade-check.log /tmp/data-saver-check.log /tmp/manual-check.log /tmp/manual-app-check.log /tmp/shizuku-check.log
