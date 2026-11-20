import runpy
import unittest

surface = runpy.run_path("scripts/check-apk-surface.py")


def dump(permissions):
    return "\n".join("uses-permission: name='" + p + "'" for p in sorted(permissions))


class ApkSurfaceTest(unittest.TestCase):
    def test_minimal_manifest(self):
        self.assertEqual([], surface["violations"](dump(surface["REQUIRED"]), "manifest"))

    def test_unused_permission_and_component_are_rejected(self):
        text = dump(surface["REQUIRED"] | {"android.permission.SYSTEM_ALERT_WINDOW"})
        errors = surface["violations"](text, "service .core.overlay.OverlayService")
        self.assertEqual(2, len(errors))
        self.assertIn("SYSTEM_ALERT_WINDOW", errors[0])
        self.assertIn("OverlayService", errors[1])

    def test_incomplete_manifest_is_not_accepted(self):
        self.assertTrue(surface["violations"]("", ""))
        self.assertTrue(surface["violations"](dump({"android.permission.INTERNET"}), ""))

    def test_sdk_specific_permissions_are_checked(self):
        text = dump(surface["REQUIRED"]) + "\nuses-permission-sdk-23: name='android.permission.READ_PHONE_STATE'"
        self.assertTrue(any("READ_PHONE_STATE" in p for p in surface["violations"](text, "")))


if __name__ == "__main__":
    unittest.main()
