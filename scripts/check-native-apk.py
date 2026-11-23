#!/usr/bin/env python3
"""Check packaged Rust ABI libraries and their ELF load-segment alignment."""
import struct
import sys
import zipfile

ABIS = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86": (1, 3), "x86_64": (2, 62)}
LIBRARY = "libnetoptimizer_core.so"


def elf_errors(data, elf_class, machine):
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4] != elf_class or data[5] != 1:
        return ["invalid ELF class or byte order"]
    try:
        header = struct.unpack_from("<16sHHIIIIIHHHHHH" if elf_class == 1 else "<16sHHIQQQIHHHHHH", data)
        if header[2] != machine:
            return ["wrong ELF machine"]
        offset, entry_size, count = header[5], header[9], header[10]
        fmt = "<IIIIIIII" if elf_class == 1 else "<IIQQQQQQ"
        if count == 0 or entry_size < struct.calcsize(fmt) or offset + entry_size * count > len(data):
            return ["invalid program header table"]
        loads = 0
        for i in range(count):
            fields = struct.unpack_from(fmt, data, offset + i * entry_size)
            if fields[0] != 1:  # PT_LOAD
                continue
            loads += 1
            file_offset, vaddr, alignment = (fields[1], fields[2], fields[7]) if elf_class == 1 else (fields[2], fields[3], fields[7])
            if alignment < 16384 or alignment & (alignment - 1) or file_offset % 16384 != vaddr % 16384:
                return ["ELF load segment is not 16 KiB aligned"]
        return [] if loads else ["ELF has no load segments"]
    except (struct.error, OverflowError):
        return ["malformed ELF"]


def check_apk(path):
    errors = []
    with zipfile.ZipFile(path) as apk:
        expected = {f"lib/{abi}/{LIBRARY}" for abi in ABIS}
        actual = {name for name in apk.namelist() if name.startswith("lib/") and name.endswith("/" + LIBRARY)}
        if expected != actual:
            errors.append(f"native ABI set mismatch: expected {sorted(expected)}, got {sorted(actual)}")
        for abi, (elf_class, machine) in ABIS.items():
            name = f"lib/{abi}/{LIBRARY}"
            if name not in actual:
                continue
            entry = apk.getinfo(name)
            # Legacy packaging extracts compressed libraries during installation. ELF alignment
            # still matters, but uncompressed APK entry alignment is then not required.
            if entry.compress_type != zipfile.ZIP_DEFLATED:
                errors.append(f"{abi}: expected compressed native library")
            errors.extend(f"{abi}: {error}" for error in elf_errors(apk.read(name), elf_class, machine))
    return errors


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check-native-apk.py path-to-apk")
    errors = check_apk(sys.argv[1])
    if errors:
        for error in errors:
            print("::error::" + error, file=sys.stderr)
        raise SystemExit(1)
    print("Rust JNI: all four expected ABIs, compressed entries and 16 KiB ELF alignment verified")


if __name__ == "__main__":
    main()
