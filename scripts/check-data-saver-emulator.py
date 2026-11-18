#!/usr/bin/env python3
"""Destructive check: only on a disposable emulator, never a personal device."""
import os
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 2:
    raise SystemExit("Only use on a disposable emulator: debug-apk")
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start = h["adb"], h["install"], h["start"]
tap, verify, alive = h["tap"], h["verify_screen"], h["alive"]

adb("uninstall", h["PACKAGE"], check=False)
adb("logcat", "-c")
adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false")
try:
    install(sys.argv[1])
    start()
    tap("Продолжить")
    tap("Снизить нагрузку на сеть")
    verify("Экономия трафика Android: выключена")
    tap("Настроить экономию трафика")
    # The exact settings screen is vendor-dependent; on AOSP it must leave the app.
    focus = adb("shell", "dumpsys", "activity", "activities")
    if "com.android.settings" not in focus:
        raise RuntimeError("Android settings did not open")
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "true")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(3)
    alive()
    verify("Экономия трафика Android: включена")
    print("Data Saver settings opened and status refreshed after returning")
finally:
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false", check=False)
