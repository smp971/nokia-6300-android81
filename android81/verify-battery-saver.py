"""Verify Android saver under simulated unplug; always reset the battery service.
Does not measure physical battery consumption. Requires installed BatteryTests.
"""
from pathlib import Path
import subprocess, sys
runner=Path(__file__).with_name("framework-command.py")
def run(*args):
    return subprocess.run([sys.executable,str(runner),"--timeout","55",*args],check=True)
try:
    run("/system/bin/dumpsys","battery","unplug")
    run("/system/bin/sh","/system/bin/am","instrument","-w","ro.leo.visual.tests/ro.leo.visual.BatteryTests")
finally:
    run("/system/bin/dumpsys","battery","reset")
