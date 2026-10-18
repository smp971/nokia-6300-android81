"""Exercise the shared call controls offline; never starts a Telecom call."""
from pathlib import Path
import shlex
import struct
import subprocess
from PIL import Image

here = Path(__file__).resolve().parent
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
out = here / 'output/call-keypad-test'
out.mkdir(parents=True, exist_ok=True)

def java(*args):
    return subprocess.check_output(['python', str(here / 'framework-command.py'),
                                    '/system/bin/sh', *args], text=True, encoding='utf-8', timeout=60)

def native(*args):
    return subprocess.check_output(adb + ['shell', shlex.join(
        ['/sbin/leo-framework-exec', *args])], text=True, encoding='utf-8', timeout=45)

def start():
    java('/system/bin/input', 'keyevent', '224')
    java('/system/bin/am', 'force-stop', 'com.android.dialer')
    result = java('/system/bin/am', 'start', '-W', '-n',
                  'com.android.dialer/com.android.incallui.LeoCallPreview')
    assert 'Error:' not in result and 'Status: ok' in result, result

def key(*codes):
    java('/system/bin/input', 'keyevent', *map(str, codes))

def snapshot(name, expected):
    native('/system/bin/rm', '-f', '/tmp/leo-call-preview.xml')
    result = java('/system/bin/uiautomator', 'dump', '/tmp/leo-call-preview.xml')
    assert 'dumped to' in result, result
    xml = native('/system/bin/cat', '/tmp/leo-call-preview.xml')
    assert expected in xml, 'Missing preview state: ' + expected
    native('/system/bin/screencap', '/tmp/leo-call-preview.raw')
    raw = out / (name + '.raw')
    subprocess.run(adb + ['pull', '/tmp/leo-call-preview.raw', str(raw)], check=True,
                   stdout=subprocess.DEVNULL)
    data = raw.read_bytes()
    width, height, fmt, stride = struct.unpack('<4I', data[:16])
    assert fmt == 1 and len(data[16:]) == width * height * 4
    Image.frombytes('RGBA', (width, height), data[16:]).save(out / (name + '.png'))
    print('PASS', name)

start()
snapshot('incoming', 'Apel primit')
key(23)
snapshot('answered', 'Apel activ')
key(23)
snapshot('muted', 'Microfon oprit')
key(22, 23)
snapshot('speaker', 'Difuzor')
key(8)
snapshot('dtmf', 'DTMF 1 eliberat')
key(82)
snapshot('options', 'Opțiuni apel')
key(4)  # This dismisses the dialog, without disconnecting the preview call.
snapshot('options-dismissed', 'Apel activ')
key(4)
snapshot('ended', 'Apel încheiat')
start()
key(4)
snapshot('rejected', 'Apel încheiat')
start()
snapshot('ready-for-physical-test', 'Apel primit')
print('Offline preview ready; no Telecom calls were created')
