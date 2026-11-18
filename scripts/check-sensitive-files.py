#!/usr/bin/env python3
"""Basic checks of the current Git working tree; not a history or full secret scan.

Only paths and check names are printed, never matching content. Use a dedicated
secret scanner and rotate exposed credentials as well; removing files is not rotation.
"""
import re
import subprocess
from pathlib import Path


def blocked_path(path: str) -> bool:
    name = Path(path).name.lower()
    if name == ".env.example":
        return False
    return (name == ".env" or name.startswith(".env.") or
            name in {"local.properties", "keystore.properties", "signing.properties", "crash.log"} or
            name.endswith((".jks", ".keystore", ".p12", ".pfx", ".pem", ".pk8", ".key")))


CHECKS = {
    "private key block": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----"),
    "GitHub token": re.compile(rb"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{50,}\b"),
    "AWS access key": re.compile(rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "Slack token": re.compile(rb"\bxox[baprs]-[A-Za-z0-9-]{20,}\b"),
    "literal Android signing password": re.compile(
        rb"(?im)^\s*(?:storePassword|keyPassword|ANDROID_KEYSTORE_PASSWORD|ANDROID_KEY_PASSWORD)\s*[:=]\s*['\"][^'\"\r\n]{3,}['\"]"
    ),
}


def main() -> int:
    output = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"])
    paths = sorted(set(p.decode("utf-8", errors="replace") for p in output.split(b"\0") if p))
    findings = []
    skipped = 0
    for path in paths:
        file = Path(path)
        if blocked_path(path):
            findings.append((path, "sensitive filename"))
            continue
        if not file.is_file() or file.is_symlink():
            skipped += 1
            continue
        # Large and binary files cannot be meaningfully scanned with these text patterns.
        if file.stat().st_size > 2_000_000:
            skipped += 1
            continue
        data = file.read_bytes()
        if data.startswith(bytes.fromhex("feedfeed")):
            findings.append((path, "Java keystore file"))
            continue
        if b"\0" in data[:8192]:
            skipped += 1
            continue
        for check, pattern in CHECKS.items():
            if pattern.search(data):
                findings.append((path, check))
    if findings:
        for path, check in findings:
            print(f"POSSIBLE SENSITIVE FILE: {path} ({check})")
        print("Review findings privately; do not paste matching values into logs or chat.")
        return 1
    print(f"Basic current-tree checks passed for {len(paths)} files ({skipped} not scanned as text). Git history was not checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
