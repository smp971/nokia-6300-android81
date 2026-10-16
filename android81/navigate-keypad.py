"""Navigate through the installed matrix_keypad.kl using raw Linux key events."""
import argparse
from pathlib import Path
import struct
import subprocess
import time

ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
HELPER = '/sbin/leo-framework-exec '
parser = argparse.ArgumentParser()
parser.add_argument('key', choices=['HOME', 'BACK', 'MENU', 'DPAD_UP', 'DPAD_DOWN',
                                  'DPAD_LEFT', 'DPAD_RIGHT', 'DPAD_CENTER'])
parser.add_argument('--capture', action='store_true')
args = parser.parse_args()

def call(*arguments):
    result = subprocess.run(ADB + list(arguments), capture_output=True, text=True,
                            encoding='utf-8', errors='replace', timeout=30)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()

assert 'uid=0' in call('shell', '/system/bin/busybox id')
assert 'androidboot.serialno=695d' in call('shell', '/system/bin/busybox cat /proc/cmdline')
assert call('shell', '/system/bin/busybox cat /sys/class/input/event1/device/name') == 'matrix_keypad'
layout = call('shell', HELPER + '/system/bin/cat /system/usr/keylayout/matrix_keypad.kl')
matches = [int(parts[1]) for line in layout.splitlines()
           if len(parts := line.split()) >= 3 and parts[0] == 'key' and parts[2] == args.key]
assert len(matches) == 1, 'Expected one installed key mapping'
code = matches[0]

def event(event_type, value):
    call('shell', HELPER + f'/system/bin/sendevent /dev/input/event1 {event_type} {code if event_type else 0} {value}')

try:
    event(1, 1)
    event(0, 0)
    time.sleep(0.08)
finally:
    event(1, 0)
    event(0, 0)
print(f'Injected scan code {code} through installed layout: {args.key}')
if args.capture:
    from PIL import Image
    time.sleep(2)
    output = Path(__file__).parent/'output'
    call('shell', HELPER + '/system/bin/screencap /tmp/leo-keypad-screen.raw')
    raw = output/('keypad-'+args.key.lower()+'.raw')
    call('pull', '/tmp/leo-keypad-screen.raw', str(raw))
    data = raw.read_bytes()
    width, height, pixel_format, _ = struct.unpack('<4I', data[:16])
    assert (width, height, pixel_format) == (240, 320, 1)
    assert len(data) == 16 + width*height*4
    path = raw.with_suffix('.png')
    Image.frombytes('RGBA', (width, height), data[16:]).save(path)
    print(path.resolve())
