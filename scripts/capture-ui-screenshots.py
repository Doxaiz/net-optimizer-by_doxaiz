#!/usr/bin/env python3
"""Capture screenshots of main screens on a disposable emulator for visual review."""
import os
import runpy
import subprocess
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 2:
    raise SystemExit("Only use on a disposable emulator: app-apk")
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start, tap = h["adb"], h["install"], h["start"], h["tap"]

OUT = os.environ.get("SHOT_DIR", "screenshots")
os.makedirs(OUT, exist_ok=True)


def shot(name):
    path = os.path.join(OUT, name + ".png")
    with open(path, "wb") as fh:
        fh.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=subprocess.PIPE, check=True).stdout)
    print("saved", path, flush=True)


adb("uninstall", h["PACKAGE"], check=False)
try:
    install(sys.argv[1])
    adb("shell", "svc", "data", "enable")
    start()
    time.sleep(2)
    shot("01-onboarding")
    tap("Продолжить")
    time.sleep(2)
    shot("02-home")
    tap("Инструменты")
    time.sleep(3)
    shot("03-tools")
    for i in range(1, 3):
        adb("shell", "input", "swipe", "540", "1500", "540", "600", "900")
        time.sleep(2)
        shot("04-tools-scroll-%d" % i)
    tap("Замеры")
    time.sleep(3)
    shot("05-tools-tests")
    for i in range(1, 4):
        adb("shell", "input", "swipe", "540", "1500", "540", "600", "900")
        time.sleep(2)
        shot("05-tools-tests-scroll-%d" % i)
    tap("Трафик")
    time.sleep(3)
    shot("06-traffic")
    for _ in range(2):
        adb("shell", "input", "swipe", "540", "1500", "540", "600", "900")
        time.sleep(1)
    time.sleep(1)
    shot("07-traffic-history")
    tap("Сессии")
    time.sleep(3)
    shot("08-sessions")
    tap("Настройки")
    time.sleep(3)
    shot("09-settings")
finally:
    pass
