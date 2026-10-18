"""Capture the native prototype and verify keypad launch/return without calls."""
from pathlib import Path
import struct
import subprocess
import time
from PIL import Image

ROOT = Path(__file__).resolve().parent
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
OUT = ROOT / 'output/visual-shell-test'
OUT.mkdir(parents=True, exist_ok=True)

def native(*args):
    return subprocess.check_output(ADB + ['shell', '/sbin/leo-framework-exec', *args], timeout=30).decode('utf-8', errors='replace')

def java(*args):
    return subprocess.check_output(['python', str(ROOT / 'framework-command.py'), '/system/bin/sh', *args], timeout=45).decode('utf-8', errors='replace')

def key(*keys):
    java('/system/bin/input', 'keyevent', *map(str, keys))

def wait_focus(expected):
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        focus = native('/system/bin/dumpsys', 'window', 'windows').split('mCurrentFocus=')[-1].splitlines()[0]
        if expected in focus.lower():
            return
        time.sleep(.2)
    raise AssertionError('Expected focus: ' + expected + ', got ' + focus)

def capture(name):
    wait_focus('ro.leo.visual')
    native('/system/bin/screencap', '/tmp/leo-visual.raw')
    raw = OUT / (name + '.raw')
    subprocess.run(ADB + ['pull', '/tmp/leo-visual.raw', str(raw)], check=True, stdout=subprocess.DEVNULL)
    data = raw.read_bytes()
    width, height, fmt, unused = struct.unpack('<4I', data[:16])
    assert fmt == 1 and len(data[16:]) == width * height * 4
    Image.frombytes('RGBA', (width, height), data[16:]).save(OUT / (name + '.png'))
    print('Captured', name)

key(224)
java('/system/bin/am', 'start', '-W', '-f', '0x14000000', '-n', 'ro.leo.visual/.MainActivity')
capture('home')
key(23)
capture('grid')
key(22, 20)
capture('calculator-selected')
key(23)
wait_focus('calculator')
print('PASS keypad launches installed Calculator')
key(4)
capture('returned')
key(4)
capture('home-returned')
key(82)
wait_focus('statusbar')
print('PASS notifications open from HOME')
key(4)
key(23, 82)
capture('options')
key(4)
key(4)
capture('ready')
assert 'ro.leo.visual' in native('/system/bin/cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
print('PASS Leo Visual is HOME; no calls placed')
