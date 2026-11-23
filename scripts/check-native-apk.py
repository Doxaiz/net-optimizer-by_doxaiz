#!/usr/bin/env python3
"""Check native APK entries, ELF load alignment and 16 KiB RELRO safety.

This is a structural check, not a substitute for testing all code paths on a
16 KiB Android device. Native libraries are required to be compressed because
this project uses legacy JNI packaging (libraries are extracted on install).
"""
import struct
import sys
import zipfile
from collections import Counter

ABIS = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86": (1, 3), "x86_64": (2, 62)}
LIBRARY = "libnetoptimizer_core.so"
PAGE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552
PF_W = 2


def elf_errors(data, elf_class, machine, require_16k=True):
    if len(data) < 64 or data[:4] != b"\x7fELF" or data[4] != elf_class or data[5] != 1 or data[6] != 1:
        return ["invalid ELF class, byte order or version"]
    try:
        header = struct.unpack_from("<16sHHIIIIIHHHHHH" if elf_class == 1 else "<16sHHIQQQIHHHHHH", data)
        if header[1] != 3 or header[3] != 1:
            return ["ELF is not a supported shared object"]
        if header[2] != machine:
            return ["wrong ELF machine"]
        offset, entry_size, count = header[5], header[9], header[10]
        fmt = "<IIIIIIII" if elf_class == 1 else "<IIQQQQQQ"
        if count == 0 or entry_size < struct.calcsize(fmt) or offset + entry_size * count > len(data):
            return ["invalid program header table"]
        loads, relros = [], []
        for i in range(count):
            f = struct.unpack_from(fmt, data, offset + i * entry_size)
            if elf_class == 1:
                kind, file_offset, vaddr, _, file_size, mem_size, flags, alignment = f
            else:
                kind, flags, file_offset, vaddr, _, file_size, mem_size, alignment = f
            if kind == PT_LOAD:
                if file_size > mem_size or file_offset + file_size > len(data):
                    return ["invalid ELF load segment bounds"]
                if alignment not in (0, 1) and (alignment & (alignment - 1) or file_offset % alignment != vaddr % alignment):
                    return ["invalid ELF load segment alignment"]
                if require_16k and (alignment < PAGE or file_offset % PAGE != vaddr % PAGE):
                    return ["ELF load segment is not 16 KiB aligned"]
                loads.append((vaddr, vaddr + mem_size, flags))
            elif kind == PT_GNU_RELRO:
                relros.append((vaddr, vaddr + mem_size))
        if not loads:
            return ["ELF has no load segments"]
        if require_16k:
            for start, end in relros:
                # Android's guidance: if writable data follows RELRO in its LOAD,
                # the RELRO end must land on a 16 KiB page boundary.
                for load_start, load_end, flags in loads:
                    if flags & PF_W and load_start <= start < load_end and end <= load_end:
                        if end < load_end and end % PAGE:
                            return ["GNU_RELRO shares a 16 KiB page with writable data"]
        return []
    except (struct.error, OverflowError):
        return ["malformed ELF"]


def check_apk(path):
    errors = []
    with zipfile.ZipFile(path) as apk:
        entries = [entry for entry in apk.infolist() if entry.filename.startswith("lib/") and entry.filename.endswith(".so")]
        counts = Counter(entry.filename for entry in entries)
        for name, count in counts.items():
            if count > 1:
                errors.append(f"duplicate native library entries: {name}")
        expected = {f"lib/{abi}/{LIBRARY}" for abi in ABIS}
        actual = {name for name in counts if name.endswith("/" + LIBRARY)}
        if expected != actual:
            errors.append(f"native ABI set mismatch: expected {sorted(expected)}, got {sorted(actual)}")
        for entry in entries:
            name = entry.filename
            parts = name.split("/")
            if len(parts) != 3 or parts[1] not in ABIS or not parts[2]:
                errors.append(f"{name}: unsupported native library path or ABI")
                continue
            abi = parts[1]
            elf_class, machine = ABIS[abi]
            # Extracted libraries do not require uncompressed ZIP entry alignment.
            if entry.compress_type != zipfile.ZIP_DEFLATED:
                errors.append(f"{name}: expected compressed native library")
            # 16 KiB Android devices are 64-bit; keep our stricter all-ABI policy
            # for the Rust library, without incorrectly requiring it of other 32-bit libs.
            require_16k = elf_class == 2 or parts[2] == LIBRARY
            errors.extend(f"{name}: {error}" for error in elf_errors(apk.read(entry), elf_class, machine, require_16k))
    return errors


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: check-native-apk.py path-to-apk")
    errors = check_apk(sys.argv[1])
    if errors:
        for error in errors:
            print("::error::" + error, file=sys.stderr)
        raise SystemExit(1)
    print("Native APK: Rust ABIs, compression, ELF alignment and applicable 16 KiB RELRO checks passed")


if __name__ == "__main__":
    main()
