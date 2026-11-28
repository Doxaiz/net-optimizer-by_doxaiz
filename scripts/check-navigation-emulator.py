#!/usr/bin/env python3
"""Core navigation and offline feedback; destructive, disposable emulators only."""
import os
import re
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 2:
    raise SystemExit("Only use on a disposable emulator: app-apk")
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start, tap, verify, alive = (h[k] for k in ("adb", "install", "start", "tap", "verify_screen", "alive"))


def report_exception(kind, value, tb):
    import traceback
    traceback.print_exception(kind, value, tb)
    try:
        visible = [n.attrib.get("text") for n in h["nodes"]() if n.attrib.get("text")]
    except Exception:
        visible = []
    print("::error::" + (str(value) + "; visible=" + repr(visible)).replace("\n", " ")[:2500], flush=True)


sys.excepthook = report_exception
adb("uninstall", h["PACKAGE"], check=False)
adb("logcat", "-c")
try:
    install(sys.argv[1])
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    time.sleep(8)
    start()
    verify("Подготовим приложение")
    tap("Продолжить")
    verify("ГЛАВНАЯ")
    h["tap_scroll"]("Проверить связь", "Главная")
    verify("Включите мобильный интернет и повторите проверку.")
    for label, header in [("Инструменты", "Диагностика сети"),
                          ("Трафик", "ТРАФИК · МОБИЛЬНАЯ СЕТЬ"),
                          ("Сессии", "История соединения"),
                          ("Настройки", "НАСТРОЙКИ"),
                          ("Главная", "ГЛАВНАЯ")]:
        tap(label)
        verify(header)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(2)
    start()
    verify("ГЛАВНАЯ")
    alive()
    log = adb("logcat", "-d", "-s", "AndroidRuntime:E", check=False)
    if re.search(r"FATAL EXCEPTION[\s\S]{0,1500}Process: " + re.escape(h["PACKAGE"]) + r"\b", log):
        raise RuntimeError("App fatal exception in AndroidRuntime: " + log[-3000:])
    print("Fresh onboarding, offline feedback, all tabs and return from background passed")
finally:
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
