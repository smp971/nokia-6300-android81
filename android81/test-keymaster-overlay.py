"""Test the missing Keymaster HAL using a volatile directory bind mount."""
from pathlib import Path
import hashlib
import json
import subprocess

HERE = Path(__file__).resolve().parent
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
SOURCE = Path(r'\\wsl.localhost\kaios\root\android81-leo\src\out\target\product\leo\system\vendor\lib\hw\android.hardware.keymaster@3.0-impl.so')

def call(*args):
    result = subprocess.run(ADB + list(args), capture_output=True, text=True, timeout=60)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()

assert 'androidboot.serialno=695d' in call('shell', '/system/bin/busybox cat /proc/cmdline')
assert 'uid=0' in call('shell', '/system/bin/busybox id')
assert SOURCE.is_file()
checksum = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
call('push', str(SOURCE), '/tmp/leo-keymaster-impl.so')
assert call('shell', '/system/bin/busybox sha256sum /tmp/leo-keymaster-impl.so').split()[0] == checksum
script = '''set -e
test ! -e /tmp/leo-keymaster-hw
cp -a /system/vendor/lib/hw /tmp/leo-keymaster-hw
cp /tmp/leo-keymaster-impl.so /tmp/leo-keymaster-hw/android.hardware.keymaster@3.0-impl.so
chmod 644 /tmp/leo-keymaster-hw/android.hardware.keymaster@3.0-impl.so
mount -o bind /tmp/leo-keymaster-hw /system/vendor/lib/hw
'''
import shlex
call('shell', '/sbin/leo-framework-exec /system/bin/sh -c ' + shlex.quote(script))
report = {'target_serial': '695d', 'volatile_bind_mount': True,
          'recovery_written': False, 'system_image_modified': False,
          'keymaster_impl_sha256': checksum, 'status': 'overlay-installed; service verification pending'}
(HERE/'output/keymaster-overlay.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
