import importlib.util
import unittest
from types import SimpleNamespace
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("audit_emulator", "scripts/audit-release-emulator.py")
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def node(**attrs):
    return SimpleNamespace(attrib=attrs)


class ScrollToActionTest(unittest.TestCase):
    def test_visible_action_does_not_scroll(self):
        with patch.object(audit, "tap") as tap, patch.object(audit, "adb") as adb:
            audit.tap_scroll("action", "home")
        tap.assert_called_once_with("action")
        adb.assert_not_called()

    def test_scrolls_to_action(self):
        screen = [node(text="home"), node(bounds="[0,0][640,1280]")]
        with patch.object(audit, "tap", side_effect=[RuntimeError("UI element not found: action"), None]) as tap, \
             patch.object(audit, "nodes", return_value=iter(screen)), \
             patch.object(audit, "adb") as adb, patch.object(audit.time, "sleep"):
            audit.tap_scroll("action", "home")
        self.assertEqual(tap.call_count, 2)
        adb.assert_called_once_with("shell", "input", "swipe", "320", "960", "320", "576", "500")

    def test_missing_anchor_does_not_scroll(self):
        with patch.object(audit, "tap", side_effect=RuntimeError("UI element not found: action")), \
             patch.object(audit, "nodes", return_value=iter([node(text="other")])), \
             patch.object(audit, "adb") as adb:
            with self.assertRaisesRegex(RuntimeError, "UI element not found"):
                audit.tap_scroll("action", "home")
        adb.assert_not_called()

    def test_missing_action_remains_a_failure(self):
        screen = [node(text="home"), node(bounds="[0,0][640,1280]")]
        with patch.object(audit, "tap", side_effect=RuntimeError("UI element not found: action")), \
             patch.object(audit, "nodes", side_effect=lambda: iter(screen)), \
             patch.object(audit, "adb") as adb, patch.object(audit.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "UI element not found"):
                audit.tap_scroll("action", "home")
        self.assertEqual(adb.call_count, 2)


if __name__ == "__main__":
    unittest.main()
