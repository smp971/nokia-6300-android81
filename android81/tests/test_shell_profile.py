import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location("shell_profile", Path(__file__).parents[1] / "profile-shell.py")
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)


class ResourceProfileCalculations(unittest.TestCase):
    def test_process_name_can_contain_spaces_and_parentheses(self):
        fields = ["S"] + ["0"] * 30
        fields[11], fields[12] = "125", "25"
        self.assertEqual(profile.cpu_ticks("42 (worker (main)) " + " ".join(fields)), 150)

    def test_cpu_is_one_core_percent_using_target_clock_ticks(self):
        first = {"pid": 42, "uptime_seconds": 10, "cpu_ticks": 100}
        last = {"pid": 42, "uptime_seconds": 40, "cpu_ticks": 130}
        self.assertEqual(profile.cpu_percent(first, last, 100), 1.0)

    def test_a_restarted_process_cannot_be_compared(self):
        first = {"pid": 42, "uptime_seconds": 10, "cpu_ticks": 100}
        last = {"pid": 43, "uptime_seconds": 40, "cpu_ticks": 130}
        self.assertIsNone(profile.cpu_percent(first, last, 100))

    def test_no_elapsed_window_has_no_cpu_estimate(self):
        sample = {"pid": 42, "uptime_seconds": 10, "cpu_ticks": 100}
        self.assertIsNone(profile.cpu_percent(sample, sample, 100))

    def test_tick_rate_comes_from_the_kernel_auxiliary_vector(self):
        self.assertEqual(profile.clock_ticks(struct.pack("<IIIIII", 6, 4096, 17, 100, 0, 0)), 100)

    def test_missing_tick_rate_is_rejected(self):
        with self.assertRaises(ValueError):
            profile.clock_ticks(struct.pack("<II", 0, 0))


if __name__ == "__main__":
    unittest.main()
