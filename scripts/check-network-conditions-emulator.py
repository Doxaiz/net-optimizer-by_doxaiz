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


def swipes(steps):
    w, ht = viewport()
    for start, end in steps:
        adb("shell", "input", "swipe", str(w // 2), str(int(ht * start)), str(w // 2), str(int(ht * end)), "300")
        time.sleep(.6)


def scroll_top():
    # Start well inside the list: a swipe beginning over the fixed header does nothing.
    swipes([(.55, .92)] * 10)
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
    found = find_text(lambda t: t in (GOOD, UNUSABLE) or t.startswith("Замер возможен"), step=(.55, .92))
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
    adb("shell", "settings", "put", "global", "captive_portal_mode", "2")

    # One emulator image keeps a validated cellular network, another keeps none: report which.
    first = open_conditions()
    print("::notice::Initial network conditions state: " + first, flush=True)
    if first == UNUSABLE:
        print("::notice::No validated cellular network on this emulator; the unreliable-network card is shown but the transition is not verified.", flush=True)
    else:
        deadline = time.monotonic() + 90
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
            print("::notice::Cellular network was never invalidated by the captive-portal probe; comparison guard not verified in UI. "
                  + "captive_portal_mode=" + adb("shell", "settings", "get", "global", "captive_portal_mode", check=False).strip()
                  + "; validated=" + str("VALIDATED" in networks), flush=True)
        else:
            if find_text(lambda t: t.startswith("Система обнаружила captive portal") or t.startswith("Android не подтвердил доступ")) is None:
                raise RuntimeError("Unreliable network reason not shown: " + repr(texts()))
            print("::notice::Invalid cellular network is reported in the UI after the failed validation probe", flush=True)
finally:
    adb("shell", "settings", "put", "global", "captive_portal_mode", restored or "1", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
