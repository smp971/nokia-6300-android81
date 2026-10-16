"""Guarded, explicitly destructive internal Android installation on serial 695d.

Requires verified live KaiOS backups. Leaves recovery and every radio partition alone.
Without --install --format-userdata, only stages and verifies files on the SD card.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shlex
import subprocess

HERE = Path(__file__).resolve().parent
OUT = HERE/'output'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
BB = '/system/bin/busybox '
PARENT = '/sbin/leo-framework-exec '
parser = argparse.ArgumentParser()
parser.add_argument('--install', action='store_true')
parser.add_argument('--format-userdata', action='store_true')
args = parser.parse_args()
assert args.install == args.format_userdata, 'Installation requires explicit userdata formatting flag'

def call(command, timeout=180):
    result = subprocess.run(ADB+['shell', command], capture_output=True, text=True,
                            encoding='utf-8', errors='replace', timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stdout+result.stderr)
    return result.stdout.strip()

def transfer(source, destination):
    result = subprocess.run(ADB+['push', str(source), destination], capture_output=True,
                            text=True, encoding='utf-8', errors='replace', timeout=900)
    if result.returncode:
        raise RuntimeError(result.stdout+result.stderr)

def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1048576), b''): value.update(chunk)
    return value.hexdigest()

def save(stage, **extra):
    report.update(stage=stage, **extra)
    (OUT/'internal-install.json').write_text(json.dumps(report, indent=2))
    print(stage, flush=True)

assert 'uid=0' in call(BB+'id')
assert 'androidboot.serialno=695d ' in call(BB+'cat /proc/cmdline')
backups = json.loads((HERE/'backup-live/kaios-before-internal/checksums.json').read_text())
assert backups['target_serial'] == '695d' and backups['read_only_backup']
for name, info in backups['partitions'].items():
    assert name in ('boot', 'system') and sha(info['path']) == info['sha256']
assert set(backups['partitions']) == {'boot', 'system'}
nodes = {}
for name, number, major, minor, size in [('boot',29,179,29,33554432),
        ('system',30,179,30,838860800), ('userdata',39,259,7,2566897152),
        ('recovery',33,259,1,33554432)]:
    values = dict(line.split('=',1) for line in call(BB+f'cat /sys/class/block/mmcblk0p{number}/uevent').splitlines() if '=' in line)
    assert values['PARTNAME'] == name and int(values['MAJOR']) == major and int(values['MINOR']) == minor
    assert int(call(BB+f'cat /sys/class/block/mmcblk0p{number}/size'))*512 == size
    node = '/dev/leo-install-'+name
    call(f'if [ ! -b {node} ]; then {BB}mknod {node} b {major} {minor}; fi')
    assert call(BB+'stat -c %t:%T '+node) == f'{major:x}:{minor:x}'
    nodes[name] = node
for name in ('boot','system'):
    assert call(BB+'md5sum '+nodes[name]).split()[0] == backups['partitions'][name]['md5'], 'Current '+name+' differs from verified backup'
recovery_md5 = call(BB+'md5sum '+nodes['recovery']).split()[0]
assert recovery_md5 == 'c9dc5f8b611368906b7b3bce5448272f', 'Unexpected recovery; preserve and review'
boot = OUT/'boot-android81-internal.img'
boot_meta = json.loads(boot.with_suffix('.json').read_text())
runtime = json.loads((OUT/'framework-runtime.json').read_text())
assert boot_meta['boot_from_internal_partitions'] and boot_meta['recovery_partition_unchanged']
assert boot.stat().st_size == 33554432 and sha(boot) == boot_meta['sha256']
system = runtime['images']['system.raw.img']
assert system['bytes'] == 838860800 and system['sha256'] == boot_meta['framework_system_image_sha256']
compressed = system['compressed']
source = Path('\\\\wsl.localhost\\kaios'+compressed['path'].replace('/','\\'))
assert source.stat().st_size == compressed['bytes'] and sha(source) == compressed['sha256']

# SD is used only as a transfer source. Internal Android will not mount it at boot.
assert call(BB+'cat /sys/class/block/mmcblk1/device/type') == 'SD'
assert call(BB+'cat /sys/class/block/mmcblk1p1/dev') == '179:65'
call(BB+'mkdir -p /tmp/leo-install-sd')
call('if [ ! -b /dev/leo-install-sd ]; then '+BB+'mknod /dev/leo-install-sd b 179 65; fi')
if ' /tmp/leo-install-sd ' not in call(BB+'cat /proc/mounts'):
    call(BB+'mount -t vfat -o rw /dev/leo-install-sd /tmp/leo-install-sd')
base = '/tmp/leo-install-sd/leo-android81'
assert call(BB+'cat '+base+'/owner') == 'LEO_ANDROID81_DIAGNOSTIC_695d'
folder = base+'/internal-install'
call(BB+'mkdir -p '+folder)
image = folder+'/system.raw.img'
if call('if [ -f '+image+' ]; then echo yes; fi') != 'yes':
    print('Transferring internal system image...', flush=True)
    transfer(source, image+'.gz')
    call(BB+'gunzip -c '+image+'.gz > '+image+'.incoming', timeout=900)
    assert call(BB+'sha256sum '+image+'.incoming').split()[0] == system['sha256']
    call(BB+'mv '+image+'.incoming '+image)
assert call(BB+'stat -c %s '+image) == '838860800'
assert call(BB+'sha256sum '+image).split()[0] == system['sha256']
transfer(boot, '/tmp/leo-internal-boot.img')
assert call(BB+'sha256sum /tmp/leo-internal-boot.img').split()[0] == boot_meta['sha256']
call(BB+'sync')
report = {'target_serial':'695d', 'system_sha256':system['sha256'],
          'boot_sha256':boot_meta['sha256'], 'recovery_md5':recovery_md5,
          'radio_partitions_written':False, 'userdata_format_authorized':args.format_userdata}
save('staged-and-verified')
if not args.install: raise SystemExit(0)

mounts = call(BB+'cat /proc/mounts')
if ' /tmp/leo-internal-check ' in mounts:
    call(BB+'umount /tmp/leo-internal-check')
for prefix in ('', PARENT):
    info = call((BB if not prefix else prefix+'/system/bin/')+'cat /proc/self/mountinfo')
    for line in info.splitlines():
        assert line.split()[2] not in ('179:30','259:7'), 'Target internal filesystem is mounted'
save('writing-system')
call(BB+'dd if='+image+' of='+nodes['system']+' bs=1048576', timeout=900)
call(BB+'sync')
assert call(BB+'sha256sum '+nodes['system']).split()[0] == system['sha256']
save('system-written-verified')

# Format through the current SD-backed Android tools. This node exists only
# inside init's namespace; verify major/minor there before invoking mkfs.
command = f'if [ ! -b /dev/leo-install-userdata ]; then /system/bin/mknod /dev/leo-install-userdata b 259 7; fi'
call(PARENT+'/system/bin/sh -c '+shlex.quote(command))
assert call(PARENT+'/system/bin/stat -c %t:%T /dev/leo-install-userdata') == '103:7'
save('formatting-userdata')
print(call(PARENT+'/system/bin/make_ext4fs -l 2566893568 -a data /dev/leo-install-userdata', timeout=900), flush=True)
call(BB+'sync')
save('userdata-formatted')

# Boot is written last; recovery remains the tested SD rescue environment.
call(BB+'dd if=/tmp/leo-internal-boot.img of='+nodes['boot']+' bs=4096')
call(BB+'sync')
assert call(BB+'sha256sum '+nodes['boot']).split()[0] == boot_meta['sha256']
assert call(BB+'md5sum '+nodes['recovery']).split()[0] == recovery_md5
save('installed-verified-ready-to-reboot', userdata_formatted=True)
print('Reboot explicitly after reviewing this verified result.', flush=True)
