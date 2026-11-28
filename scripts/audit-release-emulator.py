#!/usr/bin/env python3
"""Install the real released APKs and a candidate on an isolated Android emulator.

This deliberately uninstalls the package and is NOT for a personal device.
Only run on a disposable emulator without user data.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.doxaiz.netoptimizer"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, check=check).stdout


def install(path, replace=False):
    output = adb("install", *( ["-r"] if replace else [] ), path, check=False)
    if "Success" not in output:
        raise RuntimeError("Installation failed: " + output.strip())
    print("Install succeeded:", os.path.basename(path), "replace=" + str(replace))


def start():
    output = adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    if "Error" in output or "Exception" in output:
        raise RuntimeError("Could not start activity: " + output.strip())
    time.sleep(4)
    alive()


def alive():
    if not adb("shell", "pidof", PACKAGE, check=False).strip():
        output = adb("logcat", "-d", "-s", "AndroidRuntime:E", check=False)
        raise RuntimeError("App process stopped after launch/navigation. AndroidRuntime: " + output[-8000:])


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/audit-window.xml")
    raw = adb("exec-out", "cat", "/sdcard/audit-window.xml")
    return ET.fromstring(raw).iter()


def wait_node(label):
    for _ in range(8):
        for node in nodes():
            if node.attrib.get("text") == label or node.attrib.get("content-desc") == label:
                match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
                if match:
                    x1, y1, x2, y2 = map(int, match.groups())
                    if x2 > x1 and y2 > y1:
                        return ((x1 + x2) // 2, (y1 + y2) // 2)
        time.sleep(1)
        alive()
    raise RuntimeError("UI element not found: " + label)


def tap(label):
    x, y = wait_node(label)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(2)
    alive()


def tap_scroll(label, anchor):
    """Find an action on a scrollable screen without treating a missing action as success."""
    for attempt in range(3):
        try:
            tap(label)
            return
        except RuntimeError as error:
            if str(error) != "UI element not found: " + label or attempt == 2:
                raise
            current = list(nodes())
            if not any(n.attrib.get("text") == anchor for n in current):
                raise
            sizes = [re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", "")) for n in current]
            sizes = [(int(m.group(1)), int(m.group(2))) for m in sizes if m]
            if not sizes:
                raise
            width, height = max(sizes, key=lambda size: size[0] * size[1])
            print("Action outside viewport; scrolling:", label, flush=True)
            adb("shell", "input", "swipe", str(width // 2), str(int(height * .75)),
                str(width // 2), str(int(height * .45)), "500")
            time.sleep(1)


def verify_screen(label):
    wait_node(label)
    print("Screen visible:", label)


def main():
    if len(sys.argv) != 4 or os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes":
        raise SystemExit("Only use on a disposable emulator: candidate published-v1 old-v11")
    candidate, published, old = sys.argv[1:]
    print("Android API", adb("shell", "getprop", "ro.build.version.sdk").strip())
    adb("uninstall", PACKAGE, check=False)
    adb("logcat", "-c")

    # Check the exact published APK on a clean Android installation.
    install(published)
    start()
    print("Published 1.0.0 starts on a clean emulator")

    # Reproduce the 11 -> 1 downgrade with the actual old and current signed APKs.
    install(old, replace=True)
    output = adb("install", "-r", published, check=False)
    if "INSTALL_FAILED_VERSION_DOWNGRADE" not in output:
        raise RuntimeError("Expected downgrade rejection, got: " + output.strip())
    print("Confirmed Android rejects versionCode 11 -> 1: INSTALL_FAILED_VERSION_DOWNGRADE")

    # The proposed fix must update both old 11 and already installed published 1.
    install(candidate, replace=True)
    info = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=12\b", info):
        raise RuntimeError("Updated candidate versionCode is not 12")
    start()
    print("Candidate updates the old 1.1.1 APK without uninstalling")
    adb("uninstall", PACKAGE)
    install(published)
    install(candidate, replace=True)
    start()
    print("Candidate updates the published 1.0.0 APK without uninstalling")

    # Verify a clean first launch, onboarding and all five tabs without permissions.
    adb("uninstall", PACKAGE)
    install(candidate)
    start()
    verify_screen("Подготовим приложение")
    tap("Продолжить")
    verify_screen("ГЛАВНАЯ")
    for label, header in [("Инструменты", "Диагностика сети"),
                          ("Трафик", "ТРАФИК · МОБИЛЬНАЯ СЕТЬ"),
                          ("Сессии", "История соединения"),
                          ("Настройки", "НАСТРОЙКИ"),
                          ("Главная", "ГЛАВНАЯ")]:
        tap(label)
        verify_screen(header)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(2)
    start()
    verify_screen("ГЛАВНАЯ")
    output = adb("logcat", "-d", "-s", "AndroidRuntime:E", check=False)
    if re.search(r"FATAL EXCEPTION[\s\S]{0,1500}Process: " + re.escape(PACKAGE) + r"\b", output):
        raise RuntimeError("App fatal exception in AndroidRuntime: " + output[-8000:])
    print("Fresh install, first launch, five tabs and return from background succeeded")


if __name__ == "__main__":
    main()
