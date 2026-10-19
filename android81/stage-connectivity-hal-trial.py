"""Stage native HAL trials on 695d; add only the missing passthrough implementation."""
from pathlib import Path
import hashlib
import json
import shlex
import subprocess

root = Path(__file__).resolve().parent
stage = root / 'output/connectivity-hal-stage'
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']

def shell(command):
    return subprocess.check_output(adb + ['shell', '/sbin/leo-framework-exec /system/bin/sh -c '
                                         + shlex.quote(command)], timeout=30).decode().strip()

assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
manifest = json.loads((stage / 'manifest.json').read_text())
for relative, digest in manifest.items():
    source = stage / relative
    assert hashlib.sha256(source.read_bytes()).hexdigest() == digest
    destination = '/tmp/leo-connectivity/' + relative
    shell('mkdir -p ' + destination.rsplit('/', 1)[0])
    subprocess.run(adb + ['push', str(source), destination], check=True, capture_output=True)
    assert shell('sha256sum ' + destination).split()[0] == digest
    shell('chmod ' + ('755' if relative.startswith('bin/') else '644') + ' ' + destination)
name = 'hw/android.hardware.bluetooth@1.0-impl.so'
destination = '/system/vendor/lib/' + name
if shell('test -e ' + destination + ' && echo present || true'):
    assert shell('sha256sum ' + destination).split()[0] == manifest[name]
else:
    shell('mount -o remount,rw /system')
    try:
        shell('cp /tmp/leo-connectivity/' + name + ' ' + destination + '.new')
        shell('chmod 644 ' + destination + '.new')
        shell('mv ' + destination + '.new ' + destination)
        shell('restorecon ' + destination)
    finally:
        shell('mount -o remount,ro /system')
print('Native HAL trials staged; system read-only; no automatic startup')
