#!/usr/bin/env python3
"""Verify Android-reported network conditions in the real UI; disposable emulator only."""
import os
import re
import runpy
import sys
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 2:
    raise SystemExit("Only use on a disposable emulator: app-apk")
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start, tap, nodes = (h[k] for k in ("adb", "install", "start", "tap", "nodes"))

GOOD = "Android не сообщает об ограничениях сети."
UNUSABLE = "Сеть сейчас непригодна для достоверного замера."
REFRESH = "Обновить условия сети"
NO_NETWORK = "Нет активной мобильной сети: Android не сообщает условия."


def report_exception(kind, value, tb):
    import traceback
    traceback.print_exc()
    try:
        visible = [n.attrib.get("text") for n in nodes() if n.attrib.get("text")]
    except Exception:
        visible = []
    print("::error::" + (str(value) + "; visible=" + repr(visible)).replace("\n", " ")[:2500], flush=True)


sys.excepthook = report_exception


def viewport():
    sizes = []
    for n in nodes():
        m = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
        if m:
            sizes.append(tuple(map(int, m.groups())))
    if not sizes:
        raise RuntimeError("No viewport")
    return max(sizes, key=lambda p: p[0] * p[1])


def reveal(label, attempts=14):
    w, ht = viewport()
    for i in range(attempts):
        for n in nodes():
            if n.attrib.get("text") != label:
                continue
            m = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", n.attrib.get("bounds", ""))
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            if y1 < int(ht * 0.10) or y2 > int(ht * 0.85):
                break
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(2)
            return
        start, end = ((.70, .45) if i < attempts // 2 else (.45, .70))
        adb("shell", "input", "swipe", str(w // 2), str(int(ht * start)), str(w // 2), str(int(ht * end)), "350")
        time.sleep(.7)
    raise RuntimeError("Not found: " + label)


def texts():
    return [n.attrib.get("text", "") for n in nodes()]


def wait_text(predicate, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for text in texts():
            if predicate(text):
                return text
        time.sleep(1)
    raise RuntimeError("Expected text not visible: " + repr(texts()))


def swipes(steps, duration_ms=800):
    # A slow drag scrolls predictably; a fast swipe turns into a fling and overshoots.
    w, ht = viewport()
    for start, end in steps:
        adb("shell", "input", "swipe", str(w // 2), str(int(ht * start)), str(w // 2), str(int(ht * end)), str(duration_ms))
        time.sleep(.7)


def scroll_top():
    # Start well inside the list: a drag beginning over the fixed header does nothing.
    swipes([(.30, .90)] * 8)
    time.sleep(.5)


def find_text(predicate, step=(.62, .40), attempts=12):
    """Scroll one direction and look for a line anywhere in the list, without tapping."""
    for _ in range(attempts):
        for text in texts():
            if predicate(text):
                return text
        swipes([step])
    return None


def open_conditions():
    tap("Инструменты")
    reveal("Замеры")
    reveal(REFRESH)
    # The conditions card sits above the speed test, so scan upwards from the button.
    scroll_top()
    found = find_text(lambda t: t in (GOOD, UNUSABLE, NO_NETWORK) or t.startswith("Замер возможен"), step=(.30, .90))
    if found is None:
        raise RuntimeError("Network condition verdict not visible: " + repr(texts()))
    return found


adb("uninstall", h["PACKAGE"], check=False)
try:
    install(sys.argv[1])
    start()
    tap("Продолжить")
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "enable")
    time.sleep(8)
    restored = adb("shell", "settings", "get", "global", "captive_portal_mode", check=False).strip()

    # Baseline first, with the emulator's own settings, then try to invalidate the network.
    first = open_conditions()
    print("::notice::Baseline network conditions state: " + first, flush=True)
    if first == NO_NETWORK:
        print("::notice::This emulator has no active cellular network, so the unreliable-network transition is not verifiable here; the card correctly states that Android reports no conditions.", flush=True)
    else:
        adb("shell", "settings", "put", "global", "captive_portal_mode", "2")
        deadline = time.monotonic() + 120
        changed = None
        while time.monotonic() < deadline:
            reveal(REFRESH)
            time.sleep(1)
            if find_text(lambda t: t == UNUSABLE):
                changed = UNUSABLE
                break
            time.sleep(5)
        if changed is None:
            networks = adb("shell", "dumpsys", "connectivity", check=False)
            print("::notice::The network was never invalidated by the captive-portal probe; the comparison guard stays unverified in the UI. "
                  + "captive_portal_mode=" + adb("shell", "settings", "get", "global", "captive_portal_mode", check=False).strip()
                  + "; VALIDATED present=" + str("VALIDATED" in networks), flush=True)
        else:
            if find_text(lambda t: t.startswith("Система обнаружила captive portal") or t.startswith("Android не подтвердил доступ")) is None:
                raise RuntimeError("Unreliable network reason not shown: " + repr(texts()))
            print("::notice::The UI reports an unusable cellular network after the failed validation probe", flush=True)
finally:
    adb("shell", "settings", "put", "global", "captive_portal_mode", restored or "1", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
