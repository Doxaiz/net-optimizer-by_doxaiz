import importlib.util
import struct
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "check-native-apk.py"
spec = importlib.util.spec_from_file_location("native_apk", path)
native_apk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native_apk)


def fake_elf(bits, machine, alignment=16384, offset=0, vaddr=0):
    ident = b"\x7fELF" + bytes([bits, 1, 1]) + bytes(9)
    if bits == 1:
        hdr_fmt, ph_fmt = "<16sHHIIIIIHHHHHH", "<IIIIIIII"
        hdr_size, ph_size = struct.calcsize(hdr_fmt), struct.calcsize(ph_fmt)
        header = struct.pack(hdr_fmt, ident, 3, machine, 1, 0, hdr_size, 0, 0, hdr_size, ph_size, 1, 0, 0, 0)
        program = struct.pack(ph_fmt, 1, offset, vaddr, 0, 0, 0, 5, alignment)
    else:
        hdr_fmt, ph_fmt = "<16sHHIQQQIHHHHHH", "<IIQQQQQQ"
        hdr_size, ph_size = struct.calcsize(hdr_fmt), struct.calcsize(ph_fmt)
        header = struct.pack(hdr_fmt, ident, 3, machine, 1, 0, hdr_size, 0, 0, hdr_size, ph_size, 1, 0, 0, 0)
        program = struct.pack(ph_fmt, 1, 5, offset, vaddr, 0, 0, 0, alignment)
    return header + program


class NativeApkTest(unittest.TestCase):
    def test_four_architectures(self):
        for abi, (bits, machine) in native_apk.ABIS.items():
            with self.subTest(abi=abi):
                self.assertEqual([], native_apk.elf_errors(fake_elf(bits, machine), bits, machine))

    def test_apk_requires_all_abis_compression_and_no_duplicates(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'test.apk'
            with zipfile.ZipFile(apk, 'w', zipfile.ZIP_DEFLATED) as z:
                for abi, (bits, machine) in native_apk.ABIS.items():
                    z.writestr(f'lib/{abi}/{native_apk.LIBRARY}', fake_elf(bits, machine))
            self.assertEqual([], native_apk.check_apk(apk))
            with warnings.catch_warnings():
                warnings.simplefilter('ignore', UserWarning)
                with zipfile.ZipFile(apk, 'a', zipfile.ZIP_STORED) as z:
                    z.writestr(f'lib/x86/{native_apk.LIBRARY}', fake_elf(1, 3))
            errors = native_apk.check_apk(apk)
            self.assertIn('duplicate native library entries', errors)
            self.assertIn('x86: expected compressed native library', errors)

    def test_bad_machine_alignment_and_truncation(self):
        self.assertTrue(native_apk.elf_errors(fake_elf(2, 62), 2, 183))
        self.assertTrue(native_apk.elf_errors(fake_elf(2, 62, alignment=4096), 2, 62))
        self.assertTrue(native_apk.elf_errors(fake_elf(2, 62, offset=4096), 2, 62))
        self.assertTrue(native_apk.elf_errors(fake_elf(2, 62)[:64], 2, 62))
        self.assertTrue(native_apk.elf_errors(b"bad", 2, 62))


if __name__ == "__main__":
    unittest.main()
