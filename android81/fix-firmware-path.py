"""Update only the installed radio setup script's firmware-path write."""
from pathlib import Path
import hashlib
import json
import shlex
import subprocess

here = Path(__file__).resolve().parent
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
target = '/system/vendor/leo-radio/start.sh'
def call(*args):
    result = subprocess.run(adb + list(args), capture_output=True, text=True,
        encoding='utf-8', errors='replace', timeout=30)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()
def shell(command):
    return call('shell', '/sbin/leo-framework-exec /system/bin/sh -c ' + shlex.quote(command))

assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('cat /leo-framework-internal') == 'Validated internal Android partitions.'
old = shell('sha256sum ' + target).split()[0]
source = here / 'radio-runtime/start.sh'
new = hashlib.sha256(source.read_bytes()).hexdigest()
if old == new:
    print('Already updated'); raise SystemExit(0)
assert old == 'cec0d8e203368d22b53a9c10ef8f10c55943decf84475207807245dadae4d57b'
shell('cp ' + target + ' /tmp/leo-radio-start-original.sh')
backup = here / 'output/radio-start-before-firmware-path.sh'
call('pull', '/tmp/leo-radio-start-original.sh', str(backup))
assert hashlib.sha256(backup.read_bytes()).hexdigest() == old
call('push', str(source), '/tmp/leo-radio-start-fixed.sh')
assert shell('sha256sum /tmp/leo-radio-start-fixed.sh').split()[0] == new
shell("set -e; mount -o remount,rw /system; "
      "trap 'mount -o remount,ro /system' EXIT; "
      'cp /tmp/leo-radio-start-fixed.sh ' + target + '.new; '
      'chmod 755 ' + target + '.new; chown 0:0 ' + target + '.new; '
      'restorecon ' + target + '.new; mv -f ' + target + '.new ' + target + '; sync')
assert shell('sha256sum ' + target).split()[0] == new
assert ' /system ext4 ro,' in shell('cat /proc/mounts')
report = {'old_sha256': old, 'new_sha256': new, 'backup': str(backup),
          'services_restarted': False, 'boot_recovery_written': False}
(here / 'output/firmware-path-fix.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
