#!/usr/bin/env python3
"""Exercise a selected TCP endpoint on a disposable emulator, not a personal device."""
import os
import re
import runpy
import socket
import sys
import threading
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 2:
    raise SystemExit("Only use on a disposable emulator: debug-apk")

h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start, tap, verify, nodes, alive = (h[k] for k in
    ("adb", "install", "start", "tap", "verify_screen", "nodes", "alive"))


def report_exception(kind, value, tb):
    import traceback
    traceback.print_exception(kind, value, tb)
    try:
        visible = repr([(n.attrib.get("text"), n.attrib.get("enabled")) for n in nodes() if n.attrib.get("text")])
    except Exception:
        visible = "unavailable"
    print("::error::" + (str(value) + "; visible: " + visible).replace("\n", " ")[:2500], flush=True)


sys.excepthook = report_exception


def replace_text(label, value):
    tap(label)
    adb("shell", "input", "keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_A")
    adb("shell", "input", "text", value)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1)


def wait_for(label, timeout=90, scroll=False):
    deadline = time.monotonic() + timeout
    count = 0
    while time.monotonic() < deadline:
        current = list(nodes())
        if any(n.attrib.get("text") == label for n in current):
            return
        if scroll and count % 4 == 0:
            sizes = [re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", n.attrib.get("bounds", "")) for n in current]
            sizes = [(int(m.group(1)), int(m.group(2))) for m in sizes if m]
            if sizes:
                width, height = max(sizes, key=lambda size: size[0] * size[1])
                first, last = ((.70, .48) if count % 8 == 0 else (.48, .70))
                adb("shell", "input", "swipe", str(width // 2), str(int(height * first)), str(width // 2), str(int(height * last)), "350")
        count += 1
        alive()
        time.sleep(1)
    raise RuntimeError("Not visible: " + label + "; UI: " + repr([n.attrib.get("text") for n in nodes() if n.attrib.get("text")]))


def open_compare():
    tap("Оптимизировать сеть")
    tap("Сравнить задержку до и после")


# Emulator 10.0.2.2 is the host; never bind a public interface.
server = socket.socket()
server.bind(("127.0.0.1", 0))
server.listen(16)
server.settimeout(.5)
port = server.getsockname()[1]
connections = []
stop = threading.Event()


def accept_connections():
    while not stop.is_set():
        try:
            conn, addr = server.accept()
            connections.append(addr)
            conn.close()
        except socket.timeout:
            pass
        except OSError:
            break

thread = threading.Thread(target=accept_connections, daemon=True)
thread.start()
adb("uninstall", h["PACKAGE"], check=False)
adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false")
adb("shell", "dumpsys", "battery", "set", "level", "90")
adb("shell", "svc", "wifi", "disable")
adb("shell", "svc", "data", "enable")
try:
    install(sys.argv[1])
    start()
    tap("Продолжить")
    open_compare()
    tap("Адрес: yandex.ru:443")
    replace_text("yandex.ru", "10.0.2.2")
    replace_text("443", "65536")
    verify("Проверьте адрес и порт от 1 до 65535.")
    apply_nodes = [n for n in nodes() if n.attrib.get("text") == "Применить адрес"]
    if not apply_nodes or all(n.attrib.get("enabled") != "false" for n in apply_nodes):
        raise RuntimeError("Invalid port must not be accepted")
    replace_text("65536", str(port))
    tap("Применить адрес")
    verify("Адрес: 10.0.2.2:" + str(port))
    tap("Отмена")
    # Force-stop and relaunch: choice is stored, not only held by Compose.
    adb("shell", "am", "force-stop", h["PACKAGE"])
    start()
    open_compare()
    verify("Адрес: 10.0.2.2:" + str(port))
    tap("Начать")
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        if "com.android.settings" in adb("shell", "dumpsys", "activity", "activities"):
            break
        alive()
        time.sleep(1)
    else:
        raise RuntimeError("Settings did not open after baseline")
    if len(connections) != 6:
        raise RuntimeError("Expected six baseline connections to selected host and port, got " + str(len(connections)))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    wait_for("Я включил — измерить снова")
    tap("Я включил — измерить снова")
    wait_for("Замеры готовы. Проверьте настройку в Android: её состояние не подтверждено.", scroll=True)
    wait_for("Цель замера: 10.0.2.2:" + str(port), scroll=True)
    if len(connections) != 12:
        raise RuntimeError("Expected twelve connections to the same endpoint, got " + str(len(connections)))
    if "Restrict background status: disabled" not in adb("shell", "cmd", "netpolicy", "get", "restrict-background"):
        raise RuntimeError("Unexpected system policy change")
    print("Custom address, local validation, persistence and two six-connection series passed")
finally:
    stop.set()
    server.close()
    thread.join(timeout=2)
    adb("shell", "cmd", "netpolicy", "set", "restrict-background", "false", check=False)
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
    adb("shell", "dumpsys", "battery", "reset", check=False)
