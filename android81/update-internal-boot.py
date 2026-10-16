"""Update only an already-installed internal boot; never format data or flash recovery."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--expected-sha256', required=True)
args = parser.parse_args()
HERE = Path(__file__).resolve().parent
OUT = HERE/'output'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
BB = '/system/bin/busybox '

def call(*arguments):
    result = subprocess.run(ADB+list(arguments), capture_output=True, text=True,
                            encoding='utf-8', errors='replace', timeout=180)
    if result.returncode: raise RuntimeError(result.stdout+result.stderr)
    return result.stdout.strip()

assert 'uid=0' in call('shell',BB+'id')
assert 'androidboot.serialno=695d ' in call('shell',BB+'cat /proc/cmdline')
assert call('shell',BB+'cat /leo-framework-internal') == 'Validated internal Android partitions.'
for name,part,major,minor in [('boot',29,179,29),('recovery',33,259,1)]:
    values = dict(line.split('=',1) for line in call('shell',BB+f'cat /sys/class/block/mmcblk0p{part}/uevent').splitlines() if '=' in line)
    assert values['PARTNAME']==name and int(values['MAJOR'])==major and int(values['MINOR'])==minor
    assert call('shell',BB+f'cat /sys/class/block/mmcblk0p{part}/size')=='65536'
    call('shell',f'if [ ! -b /dev/leo-update-{name} ]; then {BB}mknod /dev/leo-update-{name} b {major} {minor}; fi')
    assert call('shell',BB+f'stat -c %t:%T /dev/leo-update-{name}')==f'{major:x}:{minor:x}'
assert call('shell',BB+'sha256sum /dev/leo-update-boot').split()[0]==args.expected_sha256
recovery = call('shell',BB+'md5sum /dev/leo-update-recovery').split()[0]
assert recovery=='c9dc5f8b611368906b7b3bce5448272f'
image=OUT/'boot-android81-internal.img'
meta=json.loads(image.with_suffix('.json').read_text())
assert meta['boot_from_internal_partitions'] and meta['kernel_unchanged']
assert image.stat().st_size==33554432 and hashlib.sha256(image.read_bytes()).hexdigest()==meta['sha256']
call('push',str(image),'/tmp/leo-update-boot.img')
assert call('shell',BB+'sha256sum /tmp/leo-update-boot.img').split()[0]==meta['sha256']
call('shell',BB+'dd if=/tmp/leo-update-boot.img of=/dev/leo-update-boot bs=4096')
call('shell',BB+'sync')
assert call('shell',BB+'sha256sum /dev/leo-update-boot').split()[0]==meta['sha256']
assert call('shell',BB+'md5sum /dev/leo-update-recovery').split()[0]==recovery
report={'target_serial':'695d','previous_boot_sha256':args.expected_sha256,
        'boot_sha256':meta['sha256'],'recovery_unchanged':True,'userdata_formatted':False,
        'stage':'boot-update-written-verified'}
(OUT/'internal-boot-update.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
