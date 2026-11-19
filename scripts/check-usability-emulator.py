#!/usr/bin/env python3
"""Destructive installation and UI checks: disposable Android emulator only."""
import os
import re
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 4:
    raise SystemExit("Only use on a disposable emulator: candidate published-v12 old-v11")

helpers = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start = helpers["adb"], helpers["install"], helpers["start"]
tap, verify_screen, alive = helpers["tap"], helpers["verify_screen"], helpers["alive"]
package = helpers["PACKAGE"]
candidate, published, old = sys.argv[1:]

adb("uninstall", package, check=False)
adb("logcat", "-c")
install(old)
install(candidate, replace=True)
if not re.search(r"versionCode=13\b", adb("shell", "dumpsys", "package", package)):
    raise RuntimeError("Candidate did not upgrade old version 11 to 13")
adb("uninstall", package)
install(published)
install(candidate, replace=True)
start()
print("Candidate updates both prior APKs without uninstalling")
verify_screen("Подготовим приложение")
tap("Продолжить")
verify_screen("ГЛАВНАЯ")
verify_screen("Проверить связь")

# Test the honest, actionable failure state with neither network active.
adb("shell", "svc", "wifi", "disable")
adb("shell", "svc", "data", "disable")
time.sleep(6)
tap("Проверить связь")
verify_screen("Включите мобильный интернет и повторите проверку.")
print("No-network check shows an explicit result")
tap("Оптимизировать сеть")
verify_screen("Нужен Shizuku")
tap("Отмена")
tap("Показать дополнительные действия")
verify_screen("Начать сравнение")
tap("Начать сравнение")
verify_screen("Подключите мобильный интернет; без него замер недоступен.")
print("Load comparison gives an actionable no-network state")

for label, header in [("Инструменты", "Диагностика сети"),
                      ("Сессии", "История соединения"),
                      ("Настройки", "НАСТРОЙКИ"),
                      ("Главная", "ГЛАВНАЯ")]:
    tap(label)
    verify_screen(header)
tap("Показать подробности")
verify_screen("РЕЖИМ РАБОТЫ")
tap("Скрыть подробности")
adb("shell", "input", "keyevent", "KEYCODE_HOME")
time.sleep(2)
start()
verify_screen("ГЛАВНАЯ")
alive()
print("Onboarding, action feedback, expert details, tabs and foreground return passed")
