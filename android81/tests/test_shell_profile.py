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

    def test_all_processes_includes_children_and_ime_but_not_similar_packages(self):
        self.assertEqual(profile.process_rows("PID NAME\n42 ro.leo.visual\n43 ro.leo.visual:webapps\n44 ro.leo.keypad\n45 ro.leo.visual.bad\n46 other\n"),[(42,"ro.leo.visual"),(43,"ro.leo.visual:webapps"),(44,"ro.leo.keypad")])

    def test_pss_uses_the_total_column_and_missing_is_unknown(self):
        self.assertEqual(profile.pss_kib("       TOTAL     12345  100 200\n"),12345)
        self.assertIsNone(profile.pss_kib("No process found"))

    def test_system_available_ram_is_not_inferred_from_free_plus_cached(self):
        self.assertEqual(profile.system_memory("MemTotal: 500000 kB\nMemFree: 10000 kB\nCached: 20000 kB\n"),{"MemTotal":500000,"MemFree":10000,"Cached":20000})

    def test_shared_audio_services_require_explicit_selection(self):
        self.assertEqual(profile.process_rows("41 audioserver\n42 media.codec\n"),[])
        self.assertEqual(profile.process_rows("41 audioserver\n42 media.codec\n",True),[(41,"audioserver"),(42,"media.codec")])


if __name__ == "__main__":
    unittest.main()
