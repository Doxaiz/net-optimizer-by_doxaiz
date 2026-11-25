#!/usr/bin/env python3
"""Foreground mobile-counter check on an isolated disposable emulator only."""
import os
import re
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 2:
    raise SystemExit("Only use on a disposable emulator: app-apk")
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start, tap, nodes = (h[k] for k in ("adb", "install", "start", "tap", "nodes"))


def report_exception(kind, value, tb):
    import traceback
    traceback.print_exception(kind, value, tb)
    try:
        visible = [n.attrib.get("text") for n in nodes() if n.attrib.get("text")]
    except Exception:
        visible = []
    print("::error::" + (str(value) + "; visible=" + repr(visible)).replace("\n", " ")[:2500], flush=True)


sys.excepthook = report_exception


def texts():
    return [n.attrib.get("text", "") for n in nodes()]


def reveal(label):
    for i in range(14):
        current = list(nodes())
        if any(n.attrib.get("text") == label for n in current):
            return
        sizes = []
        for n in current:
            m = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
            if m:
                sizes.append(tuple(map(int, m.groups())))
        if not sizes:
            raise RuntimeError("No viewport")
        w, ht = max(sizes, key=lambda p: p[0] * p[1])
        start_y, end_y = ((.72, .48) if i < 7 else (.48, .72))
        adb("shell", "input", "swipe", str(w // 2), str(int(ht * start_y)), str(w // 2), str(int(ht * end_y)), "350")
        time.sleep(.5)
    raise RuntimeError("Not found: " + label)


def scroll_sample_card(attempt):
    # Several consecutive swipes in each direction are needed for a tall result card.
    start_y, end_y = ((1750, 1100) if attempt % 8 < 4 else (1100, 1750))
    adb("shell", "input", "swipe", "540", str(start_y), "540", str(end_y), "350")


def in_app():
    return any(n.attrib.get("package") == h["PACKAGE"] for n in nodes())


def resumed_activity():
    return adb("shell", "dumpsys", "activity", "activities", check=False).strip().split("mResumedActivity")[-1][:200]


def keep_awake():
    adb("shell", "svc", "power", "stayon", "true", check=False)
    adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000", check=False)
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP", check=False)
    adb("shell", "wm", "dismiss-keyguard", check=False)


FAILURES = {"Для замера подключите мобильный интернет.", "Android не предоставил мобильные счётчики.",
            "Сеть изменилась или её нельзя проверить. Повторите замер.", "Замер прерван или счётчики сброшены. Повторите."}


def result_text(visible):
    for text in visible:
        if text in FAILURES or (text.startswith("За ") and " с: ↓ " in text):
            return text
    return None


def start_measurement(label="Измерить нагрузку за 10 секунд"):
    """Return None when measuring started, or the final text of an instantly finished probe."""
    for _ in range(3):
        reveal(label)
        if label in texts():
            tap(label)
        for _ in range(6):
            time.sleep(1)
            visible = texts()
            if "Отменить замер нагрузки" in visible:
                return None
            finished = result_text(visible)
            if finished:
                # A probe without a cellular network fails before progress becomes visible.
                return finished
        if not in_app():
            break
    raise RuntimeError("Measurement did not start; resumed=" + resumed_activity())


def wait_comparison(timeout=80):
    deadline = time.monotonic() + timeout
    attempts = 0
    while time.monotonic() < deadline:
        visible = texts()
        for text in visible:
            if text.startswith("Изменение средней передачи:") or text.startswith("Разница недоступна:"):
                return text
        if not in_app():
            raise RuntimeError("App left the foreground while waiting for the comparison")
        scroll_sample_card(attempts)
        attempts += 1
        time.sleep(1)
    raise RuntimeError("No comparison or incompatibility explanation visible; measuring=" + str("Отменить замер нагрузки" in texts()) + "; resumed=" + resumed_activity())


def wait_result(timeout=80):
    deadline = time.monotonic() + timeout
    attempts = 0
    while time.monotonic() < deadline:
        visible = texts()
        finished = result_text(visible)
        if finished:
            return finished
        if not in_app():
            raise RuntimeError("App left the foreground while waiting for the measurement; resumed=" + resumed_activity())
        scroll_sample_card(attempts)
        attempts += 1
        time.sleep(1)
    raise RuntimeError("No completed measurement; measuring=" + str("Отменить замер нагрузки" in texts()) + "; resumed=" + resumed_activity())


adb("uninstall", h["PACKAGE"], check=False)
try:
    install(sys.argv[1])
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("shell", "appops", "set", h["PACKAGE"], "GET_USAGE_STATS", "ignore")
    time.sleep(8)
    start()
    tap("Продолжить")
    tap("Трафик")
    keep_awake()
    result = start_measurement() or wait_result()
    measured = result.startswith("За ")
    if measured:
        # Some emulator images keep the cellular interface and its counters after `svc data disable`.
        print("::notice::Emulator kept mobile counters after disabling data; the no-network branch was not reproduced. Using this as the first sample: " + result)
    elif result != "Для замера подключите мобильный интернет.":
        raise RuntimeError("Unexpected offline measurement result: " + result)
    # Shortcuts select a historical sort but never grant usage access or open Settings.
    for button, selected in (("История скачивания ↓", "Сортировка истории: скачивание"),
                             ("История отдачи ↑", "Сортировка истории: отдача")):
        reveal(button)
        tap(button)
        reveal(selected)
        reveal("Нужен доступ к истории использования приложений.")
        if "ignore" not in adb("shell", "appops", "get", h["PACKAGE"], "GET_USAGE_STATS"):
            raise RuntimeError("History shortcut unexpectedly changed usage access")
        if not any(n.attrib.get("package") == h["PACKAGE"] for n in nodes()):
            raise RuntimeError("History shortcut unexpectedly left the app")
    print("Direction shortcuts select history without changing permissions")
    adb("shell", "svc", "data", "enable")
    time.sleep(10)
    keep_awake()
    result = start_measurement() or wait_result()
    print("Mobile counter measurement:", result)
    if result.startswith("За "):
        if "В среднем: ↓ " not in result:
            raise RuntimeError("Successful measurement lacks average")
        if not measured:
            measured = True
            result = start_measurement() or wait_result()
            print("Second mobile counter measurement:", result)
        if measured and result.startswith("За "):
            print("Mobile load comparison:", wait_comparison())
        elif result != "Для замера подключите мобильный интернет.":
            print("::notice::Second mobile sample unavailable; comparison UI not verified: " + result)
    # Counters and virtual connectivity vary by emulator; a failure is not a zero or success.
    elif result == "Для замера подключите мобильный интернет.":
        # Print evidence instead of silently skipping the comparison scenario.
        networks = adb("shell", "dumpsys", "connectivity", check=False).split("NetworkAgentInfo")[1:4]
        print("::notice::This emulator did not expose an active cellular network; comparison UI not verified: "
              + " | ".join(n.strip().splitlines()[0][:160] for n in networks if n.strip()))
finally:
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
