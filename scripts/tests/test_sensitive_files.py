import importlib.util
import subprocess
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("safety", Path(__file__).parents[1] / "check-sensitive-files.py")
safety = importlib.util.module_from_spec(spec)
spec.loader.exec_module(safety)


class SensitiveFilesTest(unittest.TestCase):
    def test_private_filenames_are_blocked(self):
        for name in ("signing.JKS", "team/key.p12", ".env", ".env.local", "crash.log", "local.properties"):
            with self.subTest(name=name):
                self.assertTrue(safety.blocked_path(name))
        for name in (".env.example", "README.md", "app/build.gradle.kts", "banner.jpg"):
            with self.subTest(name=name):
                self.assertFalse(safety.blocked_path(name))

    def test_checks_find_generated_samples_without_real_credentials(self):
        cases = {
            "private key block": b"-----BEGIN " + b"PRIVATE KEY-----",
            "GitHub token": b"gh" + b"p_" + b"a" * 36,
            "AWS access key": b"AK" + b"IA" + b"A" * 16,
            "Slack token": b"xox" + b"b-" + b"1" * 25,
            "literal Android signing password": b"store" + b"Password = \"fake-test-only\"",
        }
        for kind, sample in cases.items():
            with self.subTest(kind=kind):
                self.assertIsNotNone(safety.CHECKS[kind].search(sample))
        self.assertIsNone(safety.CHECKS["literal Android signing password"].search(
            b"storePassword = System.getenv(\"ANDROID_KEYSTORE_PASSWORD\")"))

    def test_scans_keystore_magic_even_with_innocent_name(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            (root / "sample.dat").write_bytes(bytes.fromhex("feedfeed") + b"private-test-data")
            result = subprocess.run(["python3", str(Path(safety.__file__).resolve())], cwd=root,
                                    capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 1)
            self.assertIn("Java keystore file", result.stdout)
            self.assertNotIn("private-test-data", result.stdout)


if __name__ == "__main__":
    unittest.main()
