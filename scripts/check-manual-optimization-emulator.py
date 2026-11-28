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
    # Include the visible UI so CI failures can be distinguished from navigation regressions.
    try:
        visible = [(n.attrib.get("text"), n.attrib.get("content-desc")) for n in nodes()
                   if n.attrib.get("text") or n.attrib.get("content-desc")]
    except Exception:
        visible = "unavailable"
    frames = traceback.extract_tb(tb)
    location = " -> ".join(f"{frame.name}:{frame.lineno}" for frame in frames)
    print("::error::" + (str(value) + "; at: " + location + "; visible: " + repr(visible)).replace("\n", " ")[:3500], flush=True)


sys.excepthook = report_exception
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start = h["adb"], h["install"], h["start"]
tap, verify, alive, nodes = h["tap"], h["verify_screen"], h["alive"], h["nodes"]


def status():
    return adb("shell", "cmd", "netpolicy", "get", "restrict-background").strip()


def wait_any(texts, timeout=100):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        found = {n.attrib.get("text") for n in nodes()}
        for text in texts:
            if text in found:
                return text
        alive()
        time.sleep(1)
    raise RuntimeError("Timed out waiting for: " + repr(texts))


def wait_text(text, timeout=100):
    wait_any([text], timeout)


def wait_comparison(texts, timeout=100):
    until = time.monotonic() + timeout
    count = 0
    while time.monotonic() < until:
        current = list(nodes())
        found = {n.attrib.get("text") for n in current}
        for text in texts:
            if text in found:
                return text
        if count % 4 == 0:
            sizes = []
            for n in current:
                match = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", ""))
                if match:
                    sizes.append(tuple(map(int, match.groups())))
            if sizes:
                w, height = max(sizes, key=lambda size: size[0] * size[1])
                # Alternate directions: the result can be just above or below the viewport.
                start, end = ((.70, .48) if count % 8 == 0 else (.48, .70))
                adb("shell", "input", "swipe", str(w // 2), str(int(height * start)), str(w // 2), str(int(height * end)), "350")
        count += 1
        alive()
        time.sleep(1)
    raise RuntimeError("Comparison result not visible; current UI: " + repr([n.attrib.get("text") for n in nodes() if n.attrib.get("text")]))


def open_optimization(compare=False):
    tap("Оптимизировать сеть")
    verify("Оптимизировать сеть?")
    if compare:
        tap("Сравнить задержку до и после")
    tap("Начать")
    until = time.monotonic() + (100 if compare else 12)
    while time.monotonic() < until:
        # Activity history can contain Settings even while the comparison is still running.
        if any(n.attrib.get("package") == "com.android.settings" for n in nodes()):
            return
        alive()
        time.sleep(1)
    raise RuntimeError("Android settings did not open")


def switch_on_in_settings():
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        for node in nodes():
            a = node.attrib
            if a.get("package") == "com.android.settings" and a.get("class") in ("android.widget.Switch", "android.widget.SwitchWidget") and a.get("checkable") == "true":
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
        alive()
        time.sleep(1)
    raise RuntimeError("Android settings switch not found: " + repr([n.attrib for n in nodes()]))


adb("uninstall", h["PACKAGE"], check=False)
adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false")
# Guarantee that the first baseline cannot run even if a virtual network lingers.
adb("shell", "dumpsys", "battery", "set", "level", "10")
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
    # The default flow opens settings immediately, even without a network.
    open_optimization()
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_text("Проверили настройку?")
    verify("Экономию трафика нужно включить вручную в Android. Проверьте переключатель: приложение не может надёжно подтвердить его общесистемное состояние.")
    if status() != "Restrict background status: disabled":
        raise RuntimeError("Returning without a toggle changed Data Saver")
    tap("Понятно")
    # Comparison is optional; low battery makes it unavailable without blocking settings.
    tap("Главная")
    open_optimization(compare=True)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_text("Проверили настройку?")
    verify("Исходный замер недоступен, поэтому сравнение невозможно. Проверьте переключатель экономии трафика в Android.")
    tap("Понятно")
    adb("shell", "dumpsys", "battery", "set", "level", "90")
    adb("shell", "svc", "data", "enable")
    time.sleep(10)
    # External DNS and TCP can fail on emulators. Retry baseline, but accept and
    # report the explicitly unavailable result without claiming a comparison.
    compared = False
    for attempt in range(3):
        tap("Главная")
        open_optimization(compare=True)
        if status() == "Restrict background status: disabled":
            switch_on_in_settings()
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        wait_text("Проверили настройку?")
        texts = {n.attrib.get("text") for n in nodes()}
        if "Я включил — измерить снова" in texts:
            tap("Я включил — измерить снова")
            success = "Замеры готовы. Проверьте настройку в Android: её состояние не подтверждено."
            failure = "Повторный замер не удался. Проверьте настройку в Android."
            unavailable = "Сравнение недоступно: нет исходного замера или мобильная сеть изменилась. Состояние системной настройки не подтверждено."
            compared = wait_comparison([success, failure, unavailable]) == success
            if not compared:
                print("External TCP failed; no comparison claimed")
            break
        if "Понятно" not in texts:
            raise RuntimeError("Expected baseline result or an unavailable-measurement message")
        tap("Понятно")
        if attempt == 2:
            print("External baseline unavailable; comparison skipped")
            break
        time.sleep(5)
    if status() != "Restrict background status: enabled":
        raise RuntimeError("Data Saver unexpectedly disabled")
    print("No Shizuku: cancel, unavailable baseline, return unchanged and real Android switch passed; before/after completed:", compared)
finally:
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false", check=False)
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
    adb("shell", "dumpsys", "battery", "reset", check=False)
