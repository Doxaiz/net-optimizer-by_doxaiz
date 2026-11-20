#!/usr/bin/env python3
"""Verify the actual APK manifest, including permissions added by library manifests."""
import glob
import os
import re
import subprocess
import sys

REQUIRED = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.QUERY_ALL_PACKAGES",
}
FORBIDDEN = {
    "android.permission.READ_PHONE_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
}
DISABLED_COMPONENTS = {".core.dns.DoxaizVpnService", ".core.overlay.OverlayService"}


def violations(permissions_dump, manifest_dump):
    granted = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", permissions_dump))
    problems = []
    if not granted:
        return ["aapt did not report any APK permissions"]
    missing = REQUIRED - granted
    unwanted = FORBIDDEN & granted
    if missing:
        problems.append("Missing required permissions: " + ", ".join(sorted(missing)))
    if unwanted:
        problems.append("Unexpected unused permissions: " + ", ".join(sorted(unwanted)))
    for component in sorted(DISABLED_COMPONENTS):
        if component in manifest_dump:
            problems.append("Disabled component is registered: " + component)
    return problems


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check-apk-surface.py path-to-apk")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    tools = glob.glob(os.path.join(sdk or "", "build-tools", "*", "aapt"))
    if not tools:
        raise SystemExit("Android build-tools aapt not found")
    # Installed versions in CI are numeric, so compare their version components.
    aapt = max(tools, key=lambda p: tuple(int(x) for x in os.path.basename(os.path.dirname(p)).split(".") if x.isdigit()))
    apk = sys.argv[1]
    permissions = subprocess.check_output([aapt, "dump", "permissions", apk], text=True)
    manifest = subprocess.check_output([aapt, "dump", "xmltree", apk, "AndroidManifest.xml"], text=True)
    errors = violations(permissions, manifest)
    if errors:
        for error in errors:
            print("::error::" + error, file=sys.stderr)
        raise SystemExit(1)
    print("APK manifest: expected permissions present, unused permissions and disabled services absent")


if __name__ == "__main__":
    main()
