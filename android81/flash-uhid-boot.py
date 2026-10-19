"""Explicit boot-only UHID trial installer for serial 695d. Does not reboot."""
from pathlib import Path
import hashlib
import json
import re
import shlex
import subprocess

out = Path(__file__).resolve().parent / 'output/kernel-uhid'
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
def call(*args):
    result = subprocess.run(adb + list(args), capture_output=True, timeout=120)
    assert result.returncode == 0, result.stderr.decode(errors='replace')
    return result.stdout.decode(errors='replace').replace('\r', '').strip()
def native(*args):
    return call('shell', shlex.join(['/sbin/leo-framework-exec', *args]))
def sha(path):
    return native('/system/bin/sha256sum', path).split()[0]

metadata = json.loads((out / 'boot-uhid.json').read_text())
image = out / 'boot-uhid.img'
backup = out / 'preflash-boot.img'
assert image.stat().st_size == backup.stat().st_size == 33554432
assert hashlib.sha256(image.read_bytes()).hexdigest() == metadata['boot_sha256']
assert hashlib.sha256(backup.read_bytes()).hexdigest() == metadata['previous_boot_sha256']
assert 'androidboot.serialno=695d ' in native('/system/bin/cat', '/proc/cmdline')
assert 'uid=0' in native('/system/bin/id')
assert native('/system/bin/cat', '/leo-framework-internal') == 'Validated internal Android partitions.'
for part, name, device in [(29, 'boot', 'b3:1d'), (33, 'recovery', '103:1')]:
    props = dict(line.split('=', 1) for line in native('/system/bin/cat', f'/sys/class/block/mmcblk0p{part}/uevent').splitlines() if '=' in line)
    assert props['PARTNAME'] == name
    assert native('/system/bin/cat', f'/sys/class/block/mmcblk0p{part}/size') == '65536'
    assert native('/system/bin/stat', '-L', '-c', '%t:%T', f'/dev/block/bootdevice/by-name/{name}') == device
states = re.findall(r'mCallState=(\d+)', native('/system/bin/dumpsys', 'telephony.registry'))
assert len(states) == 2 and set(states) == {'0'}, 'Call active or state unknown'
boot = '/dev/block/bootdevice/by-name/boot'
recovery = '/dev/block/bootdevice/by-name/recovery'
assert sha(boot) == metadata['previous_boot_sha256'], 'Boot changed since backup; stop'
recovery_sha = sha(recovery)
native('/system/bin/dd', 'if='+recovery, 'of=/tmp/leo-uhid-before-recovery.img', 'bs=4096')
call('pull', '/tmp/leo-uhid-before-recovery.img', str(out / 'preflash-recovery.img'))
assert hashlib.sha256((out / 'preflash-recovery.img').read_bytes()).hexdigest() == recovery_sha
call('push', str(image), '/tmp/leo-uhid-boot.img')
assert sha('/tmp/leo-uhid-boot.img') == metadata['boot_sha256']
before = {'boot_id': native('/system/bin/cat', '/proc/sys/kernel/random/boot_id'),
          'version': native('/system/bin/cat', '/proc/version'),
          'boot_sha256': metadata['previous_boot_sha256'], 'recovery_sha256': recovery_sha,
          'connectivity_auto': native('/system/bin/getprop', 'persist.leo.connectivity.auto')}
(out / 'preflash-state.json').write_text(json.dumps(before, indent=2))
# The only partition write in this installer.
native('/system/bin/dd', 'if=/tmp/leo-uhid-boot.img', 'of='+boot, 'bs=4096')
native('/system/bin/sync')
assert sha(boot) == metadata['boot_sha256'], 'Boot readback verification failed'
assert sha(recovery) == recovery_sha, 'Recovery hash changed'
result = {'stage': 'written-readback-verified', 'boot_sha256': metadata['boot_sha256'],
          'previous_boot_sha256': before['boot_sha256'], 'recovery_unchanged': True,
          'userdata_formatted': False, 'rebooted': False}
(out / 'flash-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result, indent=2))
