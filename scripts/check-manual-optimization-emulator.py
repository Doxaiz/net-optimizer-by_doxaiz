#!/usr/bin/env python3
"""Destructive user-flow test on a disposable emulator, never a personal phone."""
import os
import re
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 2:
    raise SystemExit("Only use on a disposable emulator: debug-apk")
def report_exception(kind, value, tb):
    import traceback
    traceback.print_exception(kind, value, tb)
    # Make failures available as GitHub check annotations on isolated emulators.
    print("::error::" + str(value).replace("\n", " ")[:1500], flush=True)


sys.excepthook = report_exception
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start = h["adb"], h["install"], h["start"]
tap, verify, alive, nodes = h["tap"], h["verify_screen"], h["alive"], h["nodes"]


def status():
    return adb("shell", "cmd", "netpolicy", "get", "restrict-background").strip()


def wait_text(text, timeout=100):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        if any(n.attrib.get("text") == text for n in nodes()):
            return
        alive()
        time.sleep(1)
    raise RuntimeError("Timed out waiting for: " + text)


def reveal(text):
    for _ in range(8):
        current = list(nodes())
        if any(n.attrib.get("text") == text for n in current):
            return
        sizes = []
        for n in current:
            match = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
            if match:
                sizes.append(tuple(map(int, match.groups())))
        if not sizes:
            raise RuntimeError("Cannot determine viewport")
        w, height = max(sizes, key=lambda size: size[0] * size[1])
        adb("shell", "input", "swipe", str(w // 2), str(int(height * .72)), str(w // 2), str(int(height * .48)), "350")
        time.sleep(0.5)
    raise RuntimeError("UI element not visible: " + text + "; visible: " + repr([(n.attrib.get("text"), n.attrib.get("bounds")) for n in nodes() if n.attrib.get("text")]))


def open_optimization():
    tap("Оптимизировать сеть")
    verify("Оптимизировать сеть?")
    tap("Начать")
    until = time.monotonic() + 100
    while time.monotonic() < until:
        if "com.android.settings" in adb("shell", "dumpsys", "activity", "activities"):
            return
        alive()
        time.sleep(1)
    raise RuntimeError("Android settings did not open")


def switch_on_in_settings():
    for node in nodes():
        a = node.attrib
        if a.get("class") in ("android.widget.Switch", "android.widget.SwitchWidget") and a.get("checkable") == "true":
            if a.get("checked") == "true":
                raise RuntimeError("Expected Data Saver to start disabled")
            match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
            if not match:
                continue
            x1, y1, x2, y2 = map(int, match.groups())
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(3)
            if status() != "Restrict background status: enabled":
                raise RuntimeError("The Android settings switch did not enable Data Saver")
            return
    raise RuntimeError("Android settings switch not found: " + repr([n.attrib for n in nodes()]))


adb("uninstall", h["PACKAGE"], check=False)
adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false")
adb("shell", "dumpsys", "battery", "set", "level", "90")
adb("shell", "svc", "wifi", "disable")
adb("shell", "svc", "data", "disable")
try:
    install(sys.argv[1])
    start()
    tap("Продолжить")
    # Cancellation does not open settings or change any system policy.
    tap("Оптимизировать сеть")
    verify("Оптимизировать сеть?")
    tap("Отмена")
    if status() != "Restrict background status: disabled":
        raise RuntimeError("Cancel changed Data Saver")
    # Without a network, settings remain accessible, but no comparison is claimed.
    open_optimization()
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_text("Проверили настройку?")
    verify("Исходный замер недоступен, поэтому сравнение невозможно. Проверьте переключатель экономии трафика в Android.")
    if status() != "Restrict background status: disabled":
        raise RuntimeError("Returning without a toggle changed Data Saver")
    tap("Понятно")
    adb("shell", "svc", "data", "enable")
    time.sleep(10)
    # External DNS and TCP can fail transiently on emulators. Retry baseline, never
    # treating an unavailable measurement as an optimization success.
    for attempt in range(3):
        tap("Главная")
        open_optimization()
        if status() == "Restrict background status: disabled":
            switch_on_in_settings()
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        wait_text("Проверили настройку?")
        texts = {n.attrib.get("text") for n in nodes()}
        if "Я включил — измерить снова" in texts:
            tap("Я включил — измерить снова")
            wait_text("Замеры готовы. Проверьте настройку в Android: её состояние не подтверждено.")
            break
        if "Понятно" not in texts or attempt == 2:
            raise RuntimeError("Baseline did not complete on the emulator")
        tap("Понятно")
        time.sleep(5)
    if status() != "Restrict background status: enabled":
        raise RuntimeError("Data Saver unexpectedly disabled")
    print("No Shizuku: cancel, no network, return unchanged, real Android switch and before/after passed")
finally:
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false", check=False)
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
    adb("shell", "dumpsys", "battery", "reset", check=False)
