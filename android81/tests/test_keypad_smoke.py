import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("keypad_smoke", Path(__file__).parents[1] / "smoke-leo-keypad.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class FocusedWindowTests(unittest.TestCase):
    def test_dialog_can_share_its_home_activity_title(self):
        raw = """
  Window #0 Window{48f6b05 u0 ro.leo.visual/ro.leo.visual.MainActivity}:
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=2 fl=#80800000}
  Window #1 Window{8d5ac23 u0 ro.leo.visual/ro.leo.visual.MainActivity}:
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=1 fl=#81810100}
  mCurrentFocus=Window{48f6b05 u0 ro.leo.visual/ro.leo.visual.MainActivity}
"""
        self.assertEqual(smoke.parse_focused_window(raw)[1], 2)

    def test_accessibility_menu_is_an_overlay(self):
        raw = """
  Window #1 Window{7b1579a u0 ro.leo.visual}:
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=2032}
  mCurrentFocus=Window{7b1579a u0 ro.leo.visual}
"""
        self.assertEqual(smoke.parse_focused_window(raw), ("Window{7b1579a u0 ro.leo.visual}", 2032))

    def test_recreated_home_uses_its_current_token(self):
        raw = """
  Window #0 Window{new123 u0 ro.leo.visual/ro.leo.visual.InternalHome}:
    mAttrs=WM.LayoutParams{(0,0)(fillxfill) ty=1}
  mCurrentFocus=Window{new123 u0 ro.leo.visual/ro.leo.visual.InternalHome}
"""
        self.assertEqual(smoke.parse_focused_window(raw)[1], 1)

    def test_missing_focus_is_not_a_home(self):
        self.assertEqual(smoke.parse_focused_window("mCurrentFocus=null"), ("", None))

    def test_missing_window_attributes_is_unknown(self):
        self.assertEqual(smoke.parse_focused_window("mCurrentFocus=Window{a u0 ro.leo.visual}"),
                         ("Window{a u0 ro.leo.visual}", None))
