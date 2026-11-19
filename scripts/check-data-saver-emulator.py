#!/usr/bin/env python3
"""Destructive check: only on a disposable emulator, never a personal device."""
import os
import re
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
    tap("Трафик")
    # Emulator display sizes differ: infer actual bounds, then scroll in short steps.
    bounds = [re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", "")) for n in h["nodes"]()]
    sizes = [(int(m.group(1)), int(m.group(2))) for m in bounds if m]
    if not sizes:
        raise RuntimeError("Cannot determine emulator viewport")
    width, height = max(sizes, key=lambda size: size[0] * size[1])
    x = str(width // 2)
    for _ in range(10):
        if any(n.attrib.get("text") == "Фоновый трафик этого приложения: без ограничений" for n in h["nodes"]()):
            break
        adb("shell", "input", "swipe", x, str(int(height * 0.72)), x, str(int(height * 0.52)), "350")
        time.sleep(0.5)
    verify("Фоновый трафик этого приложения: без ограничений")
    for _ in range(10):
        if any(n.attrib.get("text") == "Настроить экономию трафика" for n in h["nodes"]()):
            break
        adb("shell", "input", "swipe", x, str(int(height * 0.72)), x, str(int(height * 0.52)), "350")
        time.sleep(0.5)
    try:
        tap("Настроить экономию трафика")
    except RuntimeError:
        print("Visible UI after attempted scroll:", flush=True)
        for node in h["nodes"]():
            if node.attrib.get("text"):
                print(node.attrib["text"], node.attrib.get("bounds", ""), flush=True)
        raise
    # The exact settings screen is vendor-dependent; on AOSP it must leave the app.
    focus = adb("shell", "dumpsys", "activity", "activities")
    if "com.android.settings" not in focus:
        raise RuntimeError("Android settings did not open")
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "true")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(3)
    alive()
    # Return to the status label if scrolling moved it above the viewport.
    for _ in range(10):
        if any(n.attrib.get("text") == "Фоновый трафик этого приложения: ограничен" for n in h["nodes"]()):
            break
        adb("shell", "input", "swipe", x, str(int(height * 0.52)), x, str(int(height * 0.72)), "350")
        time.sleep(0.5)
    verify("Фоновый трафик этого приложения: ограничен")
    print("Data Saver settings opened and status refreshed after returning")
finally:
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false", check=False)
