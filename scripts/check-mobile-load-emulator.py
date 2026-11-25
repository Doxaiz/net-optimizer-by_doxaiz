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
    w, ht = viewport()
    start_y, end_y = ((.72, .45) if attempt % 8 < 4 else (.45, .72))
    adb("shell", "input", "swipe", str(w // 2), str(int(ht * start_y)), str(w // 2), str(int(ht * end_y)), "350")


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


def notice(label, value):
    """Surface results as annotations: job archives are not always retrievable."""
    text = (label + ": " + value).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print("::notice::" + text[:2500], flush=True)


def viewport():
    sizes = []
    for n in nodes():
        m = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
        if m:
            sizes.append(tuple(map(int, m.groups())))
    if not sizes:
        raise RuntimeError("No viewport")
    return max(sizes, key=lambda p: p[0] * p[1])


def scroll_to_load_card():
    """The load card is the third item, so scroll back to the top before tapping it."""
    w, ht = viewport()
    for _ in range(6):
        adb("shell", "input", "swipe", str(w // 2), str(int(ht * 0.3)), str(w // 2), str(int(ht * 0.85)), "300")
        time.sleep(.4)
    time.sleep(1)


def tap_on_screen(label):
    """Tap only when the label is fully inside the viewport; otherwise scroll to it."""
    w, ht = viewport()
    for _ in range(10):
        for node in nodes():
            if node.attrib.get("text") != label:
                continue
            m = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", node.attrib.get("bounds", ""))
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            if y1 < int(ht * 0.12) or y2 > int(ht * 0.82) or x2 <= x1 or y2 <= y1:
                break
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(1)
            return True
        adb("shell", "input", "swipe", str(w // 2), str(int(ht * 0.75)), str(w // 2), str(int(ht * 0.5)), "350")
        time.sleep(.8)
    return False


def measure(label="Измерить нагрузку за 10 секунд", allow_stale=False):
    """Tap the button and report (started, finished_text).

    A probe without a cellular network fails before the progress indicator can be
    dumped, so an unchanged result text is only trusted when allow_stale is set.
    """
    for _ in range(3):
        scroll_to_load_card()
        stale = result_text(texts())
        if not tap_on_screen(label):
            continue
        for _ in range(6):
            time.sleep(1)
            visible = texts()
            if "Отменить замер нагрузки" in visible:
                return True, None
            finished = result_text(visible)
            if finished and (allow_stale or finished != stale):
                return False, finished
        if not in_app():
            break
    raise RuntimeError("Measurement did not start; resumed=" + resumed_activity())


def measure_live(label="Измерить нагрузку за 10 секунд", allow_stale=False):
    started, result = measure(label, allow_stale)
    return wait_result() if started else result


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
    result = measure_live()
    measured = result.startswith("За ")
    if measured:
        # Some emulator images keep the cellular interface and its counters after `svc data disable`.
        notice("Emulator kept mobile counters after disabling data; the no-network branch was not reproduced", result)
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
    # The emulated modem can take a while to come back, so retry before giving up.
    for attempt in range(4):
        result = measure_live(allow_stale=True)
        notice("Mobile counter measurement (attempt %d)" % (attempt + 1), result)
        if result.startswith("За "):
            break
        if result != "Для замера подключите мобильный интернет.":
            raise RuntimeError("Unexpected measurement result: " + result)
        time.sleep(10)
    if result.startswith("За "):
        if "В среднем: ↓ " not in result:
            raise RuntimeError("Successful measurement lacks average")
        second = measure_live(allow_stale=True)
        notice("Second mobile counter measurement", second)
        if second.startswith("За "):
            notice("Mobile load comparison", wait_comparison())
        else:
            notice("Second mobile sample unavailable; comparison UI not verified", second)
    elif result == "Для замера подключите мобильный интернет.":
        # Print evidence instead of silently skipping the comparison scenario.
        networks = adb("shell", "dumpsys", "connectivity", check=False).split("NetworkAgentInfo")[1:4]
        notice("This emulator did not expose an active cellular network; comparison UI not verified",
               " | ".join(n.strip().splitlines()[0][:160] for n in networks if n.strip()))
finally:
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
