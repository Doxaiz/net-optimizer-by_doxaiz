#!/usr/bin/env python3
"""Install and stop an isolated fixture app; NEVER run on a personal device."""
import os
import re
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 3:
    raise SystemExit("Only use on a disposable emulator: application-apk fixture-apk")


def report_exception(kind, value, tb):
    import traceback
    traceback.print_exception(kind, value, tb)
    print("::error::" + str(value).replace("\n", " ")[:1500], flush=True)


sys.excepthook = report_exception
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start, tap, verify, nodes = (h[k] for k in ("adb", "install", "start", "tap", "verify_screen", "nodes"))
fixture = "com.doxaiz.netoptfixture"


def stopped():
    info = adb("shell", "dumpsys", "package", fixture)
    match = re.search(r"User 0:.*?stopped=(true|false)", info)
    if not match:
        raise RuntimeError("Could not determine fixture stopped state")
    return match.group(1) == "true"


def fixture_uid():
    info = adb("shell", "pm", "list", "packages", "-U", fixture)
    match = re.search(r"uid:(\d+)", info)
    if not match:
        raise RuntimeError("Could not determine fixture UID")
    return match.group(1)


def restricted(uid):
    output = adb("shell", "cmd", "netpolicy", "list", "restrict-background-blacklist")
    return re.search(r"(?<!\d)" + re.escape(uid) + r"(?!\d)", output) is not None


def toggle_background_data():
    for _ in range(8):
        current = list(nodes())
        for node in current:
            a = node.attrib
            if a.get("text", "").lower() == "background data" and a.get("enabled") == "true":
                match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
                if match:
                    x1, y1, x2, y2 = map(int, match.groups())
                    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    time.sleep(3)
                    return
        sizes = []
        for node in current:
            match = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if match:
                sizes.append(tuple(map(int, match.groups())))
        if not sizes:
            raise RuntimeError("Cannot determine settings viewport")
        width, height = max(sizes, key=lambda size: size[0] * size[1])
        adb("shell", "input", "swipe", str(width // 2), str(int(height * .72)), str(width // 2), str(int(height * .52)), "350")
        time.sleep(1)
    raise RuntimeError("Background data control not found: " + repr([(n.attrib.get("text"), n.attrib.get("bounds")) for n in nodes() if n.attrib.get("text")]))


def wait_settings(timeout=100):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if "com.android.settings" in adb("shell", "dumpsys", "activity", "activities"):
            return
        time.sleep(1)
    raise RuntimeError("Android app settings did not open")


def wait_comparison(texts, timeout=100):
    deadline = time.monotonic() + timeout
    step = 0
    while time.monotonic() < deadline:
        current = list(nodes())
        found = {n.attrib.get("text") for n in current}
        for text in texts:
            if text in found:
                return text
        if step % 5 == 0:
            sizes = []
            for n in current:
                m = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
                if m:
                    sizes.append(tuple(map(int, m.groups())))
            if sizes:
                w, height = max(sizes, key=lambda s: s[0] * s[1])
                start, end = ((.70, .48) if step % 10 == 0 else (.48, .70))
                adb("shell", "input", "swipe", str(w // 2), str(int(height * start)), str(w // 2), str(int(height * end)), "350")
        step += 1
        time.sleep(1)
    raise RuntimeError("Comparison result not visible: " + repr([n.attrib.get("text") for n in nodes() if n.attrib.get("text")]))


def choose_fixture():
    tap("Настроить фон приложений")
    verify("Выберите приложение")
    tap("Поиск приложений")
    adb("shell", "input", "text", "Network")
    time.sleep(2)
    # Dismiss keyboard before selecting the list entry.
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    verify("Network Fixture")
    tap("Network Fixture")
    verify("Настроить Network Fixture?")


adb("uninstall", h["PACKAGE"], check=False)
adb("uninstall", fixture, check=False)
try:
    install(sys.argv[1])
    install(sys.argv[2])
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("shell", "dumpsys", "battery", "set", "level", "10")
    adb("shell", "appops", "set", h["PACKAGE"], "GET_USAGE_STATS", "ignore")
    adb("shell", "am", "start", "-W", "-n", fixture + "/.MainActivity")
    if stopped():
        raise RuntimeError("Fixture did not start")
    start()
    tap("Продолжить")
    tap("Трафик")
    # No usage permission, network or Shizuku needed; cancellation makes no change.
    choose_fixture()
    tap("Отмена")
    uid = fixture_uid()
    if stopped() or restricted(uid):
        raise RuntimeError("Cancellation changed the fixture")
    choose_fixture()
    # Even when an optional baseline cannot run, the user can change the setting.
    tap("Сравнить задержку до и после")
    tap("Настроить фон")
    focus = adb("shell", "dumpsys", "activity", "activities")
    if "com.android.settings" not in focus:
        raise RuntimeError("Android app data settings did not open")
    verify("Network Fixture")
    if stopped() or restricted(uid):
        raise RuntimeError("Opening data settings changed the fixture")
    toggle_background_data()
    if not restricted(uid):
        raise RuntimeError("Android did not apply per-app metered background restriction")
    if stopped():
        raise RuntimeError("Background restriction stopped the fixture")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    verify("Сравнить задержку?")
    verify("Исходный замер недоступен или сеть изменилась. Настройку фоновых данных проверьте в Android.")
    tap("Понятно")
    if not restricted(uid):
        raise RuntimeError("Restriction did not persist after returning")
    choose_fixture()
    tap("Настроить фон")
    verify("Network Fixture")
    toggle_background_data()
    if restricted(uid):
        raise RuntimeError("Android did not remove per-app restriction")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    # Try a comparison with mobile data; the public endpoint may be unavailable in CI.
    adb("shell", "dumpsys", "battery", "set", "level", "90")
    adb("shell", "svc", "data", "enable")
    time.sleep(8)
    choose_fixture()
    tap("Сравнить задержку до и после")
    tap("Настроить фон")
    wait_settings()
    verify("Network Fixture")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    verify("Сравнить задержку?")
    texts = {n.attrib.get("text") for n in nodes()}
    if "Измерить после" in texts:
        tap("Измерить после")
        result = wait_comparison([
            "Замеры готовы. Проверьте настройку в Android: её состояние не подтверждено.",
            "Повторный замер не удался. Проверьте настройку в Android.",
            "Сравнение недоступно: нет исходного замера или мобильная сеть изменилась. Состояние системной настройки не подтверждено."
        ])
        print("Per-app comparison result:", result)
    else:
        verify("Исходный замер недоступен или сеть изменилась. Настройку фоновых данных проверьте в Android.")
        tap("Понятно")
        print("Per-app comparison unavailable; settings remain usable")
    if restricted(uid):
        raise RuntimeError("No-toggle comparison unexpectedly changed the fixture policy")
    # Return to a fresh top-level traffic screen, independent of scroll position.
    tap("Главная")
    tap("Трафик")
    choose_fixture()
    tap("Остановить")
    focus = adb("shell", "dumpsys", "activity", "activities")
    if "com.android.settings" not in focus:
        raise RuntimeError("Android application settings did not open")
    if stopped():
        raise RuntimeError("Opening settings unexpectedly stopped the fixture")
    # The user, not our app, chooses Android's Force stop control.
    for node in nodes():
        a = node.attrib
        if a.get("text", "").lower() == "force stop" and a.get("enabled") == "true":
            match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
            if match:
                x1, y1, x2, y2 = map(int, match.groups())
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                break
    else:
        raise RuntimeError("Enabled Android Force stop control not found: " + repr([n.attrib for n in nodes() if n.attrib.get("text")]))
    time.sleep(1)
    for label in ("OK", "Force stop"):
        matches = [n for n in nodes() if n.attrib.get("text", "").lower() == label.lower() and n.attrib.get("enabled") == "true"]
        if matches:
            a = matches[-1].attrib
            m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", a.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                break
    time.sleep(2)
    if not stopped():
        raise RuntimeError("Android did not stop the fixture after user confirmation")
    print("Without Shizuku, usage access or network: search, cancel, per-app background restriction and restore, Android force stop passed")
finally:
    adb("uninstall", fixture, check=False)
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
    adb("shell", "dumpsys", "battery", "reset", check=False)
