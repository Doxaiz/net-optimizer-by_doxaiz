#!/usr/bin/env python3
"""Destructive update test: a disposable emulator only, not a personal phone."""
import os
import re
import runpy
import sys

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 3:
    raise SystemExit("Only use on a disposable emulator: old-apk new-apk")
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start = h["adb"], h["install"], h["start"]
tap, verify, alive = h["tap"], h["verify_screen"], h["alive"]
package = h["PACKAGE"]

adb("uninstall", package, check=False)
adb("logcat", "-c")
install(sys.argv[1])
start()
verify("Подготовим приложение")
tap("Продолжить")
verify("ГЛАВНАЯ")
install(sys.argv[2], replace=True)
info = adb("shell", "dumpsys", "package", package)
if not re.search(r"versionCode=19\b", info):
    raise RuntimeError("Updated package is not versionCode 19")
start()
# Onboarding is persisted by the old APK and must not be shown again after updating.
verify("ГЛАВНАЯ")
tap("Трафик")
verify("Настроить фон приложений")
alive()
print("Release update preserved onboarding and the new screen is reachable")
