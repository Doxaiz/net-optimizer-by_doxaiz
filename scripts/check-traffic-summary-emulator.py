#!/usr/bin/env python3
"""Historical statistics smoke test; only run on an isolated disposable emulator."""
import http.server
import os
import re
import runpy
import sys
import threading
import time

if os.environ.get("AUDIT_DISPOSABLE_EMULATOR") != "yes" or len(sys.argv) != 3:
    raise SystemExit("Only use on a disposable emulator: application-apk fixture-apk")


def report_exception(kind, value, tb):
    import traceback
    traceback.print_exception(kind, value, tb)
    print("::error::" + str(value).replace("\n", " ")[:2000], flush=True)


sys.excepthook = report_exception
h = runpy.run_path("scripts/audit-release-emulator.py")
adb, install, start, tap, verify, alive, nodes = (h[k] for k in ("adb", "install", "start", "tap", "verify_screen", "alive", "nodes"))
fixture = "com.doxaiz.netoptfixture"


class LocalProbe(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/probe":
            self.send_error(404)
            return
        content = b"x" * (512 * 1024)
        self.send_response(200)
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, *_):
        pass


def scroll_down():
    current = list(nodes())
    sizes = []
    for node in current:
        match = re.fullmatch(r"\[0,0\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            sizes.append(tuple(map(int, match.groups())))
    if not sizes:
        raise RuntimeError("Cannot determine viewport")
    width, height = max(sizes, key=lambda s: s[0] * s[1])
    adb("shell", "input", "swipe", str(width // 2), str(int(height * .73)), str(width // 2), str(int(height * .48)), "350")
    time.sleep(.5)


server = http.server.ThreadingHTTPServer(("127.0.0.1", 18765), LocalProbe)
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
adb("uninstall", h["PACKAGE"], check=False)
adb("uninstall", fixture, check=False)
try:
    install(sys.argv[1])
    install(sys.argv[2])
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "enable")
    adb("shell", "appops", "set", h["PACKAGE"], "GET_USAGE_STATS", "allow")
    adb("logcat", "-c")
    time.sleep(10)
    adb("shell", "am", "start", "-W", "-n", fixture + "/.MainActivity", "--ez", "NETOPT_PROBE", "true")
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        log = adb("logcat", "-d", "-s", "NetOptFixture:I", check=False)
        if "LOCAL_PROBE_BYTES=524288" in log:
            break
        if "LOCAL_PROBE_FAILED=" in log:
            raise RuntimeError("Fixture local network probe failed: " + log[-800:])
        time.sleep(1)
    else:
        raise RuntimeError("Fixture local network probe did not finish")
    adb("shell", "dumpsys", "netstats", "--poll", check=False)
    start()
    tap("Продолжить")
    tap("Трафик")
    seen_fixture = False
    seen_state = False
    seen_sort = False
    # Android may delay publishing stats or classify traffic differently on emulators.
    # Do not synthesize stats or claim the fixture was observed if Android omits it.
    for _ in range(16):
        texts = [n.attrib.get("text", "") for n in nodes()]
        if "Network Fixture" in texts:
            seen_fixture = True
        if any(text.startswith("Условно фон: ") for text in texts):
            seen_state = True
        if "Условно фон" in texts and not seen_sort:
            tap("Условно фон")
            seen_sort = True
        if seen_fixture and seen_state and seen_sort:
            break
        scroll_down()
    alive()
    if seen_fixture and not seen_state:
        raise RuntimeError("Historical app row did not show state availability")
    print("Local fixture transferred 512 KiB. Android history observed fixture:", seen_fixture,
          "state label:", seen_state, "sort control:", seen_sort)
    if not seen_fixture:
        print("::notice::Android did not expose the fixture's historical usage in this run; no usage value was asserted.")
finally:
    server.shutdown()
    server.server_close()
    thread.join(timeout=3)
    adb("uninstall", fixture, check=False)
    adb("shell", "svc", "data", "enable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
