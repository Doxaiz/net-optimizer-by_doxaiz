#!/usr/bin/env python3
"""Real Shizuku integration, only on a disposable emulator with no personal data."""
import os
import re
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 3:
    raise SystemExit("Only use on a disposable emulator: candidate-apk shizuku-apk")
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start = h["adb"], h["install"], h["start"]
tap, verify = h["tap"], h["verify_screen"]


def viewport():
    matches = [re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", "")) for n in h["nodes"]()]
    sizes = [(int(m.group(1)), int(m.group(2))) for m in matches if m]
    if not sizes:
        raise RuntimeError("Cannot determine emulator viewport")
    return max(sizes, key=lambda size: size[0] * size[1])


def scroll_to(label, attempts=12):
    width, height = viewport()
    for _ in range(attempts):
        if any(n.attrib.get("text") == label or n.attrib.get("content-desc") == label for n in h["nodes"]()):
            return
        adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.72)),
            str(width // 2), str(int(height * 0.53)), "350")
        time.sleep(0.5)
    print("Visible UI:", [(n.attrib.get("text"), n.attrib.get("bounds")) for n in h["nodes"]() if n.attrib.get("text")])
    raise RuntimeError("Could not find " + label)


def system_data_saver():
    return adb("shell", "cmd", "netpolicy", "get", "restrict-background").strip()


adb("uninstall", h["PACKAGE"], check=False)
adb("uninstall", "moe.shizuku.privileged.api", check=False)
adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false")
try:
    install(sys.argv[2])
    apk_path = adb("shell", "pm", "path", "moe.shizuku.privileged.api").strip().removeprefix("package:")
    if not apk_path.startswith("/data/app/") or not apk_path.endswith("/base.apk"):
        raise RuntimeError("Shizuku APK installation path missing")
    starter = apk_path.rsplit("/", 1)[0] + "/lib/x86_64/libshizuku.so"
    result = adb("shell", starter, check=False)
    if "exit with 0" not in result:
        raise RuntimeError("Could not start Shizuku via adb: " + result[-1200:])
    time.sleep(5)
    install(sys.argv[1])
    start()
    scroll_to("Разрешить")
    tap("Разрешить")
    tap("Allow all the time")
    time.sleep(3)
    verify("Shizuku · не обязательно · готово")
    tap("Продолжить")
    tap("Трафик")
    time.sleep(4)
    print("Traffic screen initially:", [(n.attrib.get("text"), n.attrib.get("bounds")) for n in h["nodes"]() if n.attrib.get("text")], flush=True)
    try:
        scroll_to("Включить через Shizuku")
    except RuntimeError:
        print("Command diagnostics:", adb("logcat", "-d", "-s", "NetOptShizuku:W", check=False)[-2000:], flush=True)
        raise
    tap("Включить через Shizuku")
    verify("Ограничить фоновые загрузки?")
    tap("Отмена")
    if system_data_saver() != "Restrict background status: disabled":
        raise RuntimeError("Cancel unexpectedly changed Data Saver")
    tap("Включить через Shizuku")
    tap("Включить")
    time.sleep(4)
    print("Policy after enable:", system_data_saver(), flush=True)
    print("After enable UI:", [(n.attrib.get("text"), n.attrib.get("bounds")) for n in h["nodes"]() if n.attrib.get("text")], flush=True)
    print("Command diagnostics:", adb("logcat", "-d", "-s", "NetOptShizuku:W", check=False)[-2000:], flush=True)
    scroll_to("Выключить через Shizuku")
    verify("Выключить через Shizuku")
    if system_data_saver() != "Restrict background status: enabled":
        raise RuntimeError("Android did not enable Data Saver")
    tap("Выключить через Shizuku")
    tap("Выключить")
    time.sleep(4)
    verify("Включить через Shizuku")
    if system_data_saver() != "Restrict background status: disabled":
        raise RuntimeError("Android did not disable Data Saver")
    print("Real Shizuku: cancel, enable, status verification and disable passed")
finally:
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false", check=False)
