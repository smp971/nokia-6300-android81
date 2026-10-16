"""Atomically replace gralloc on the internal system, with an original file backup."""
from pathlib import Path
import hashlib
import json
import shlex
import subprocess
import time

HERE=Path(__file__).resolve().parent
OUT=HERE/'output'
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
PARENT='/sbin/leo-framework-exec '
BB='/system/bin/busybox '
SOURCE=Path(r'\\wsl.localhost\kaios\root\android81-leo\src\out\target\product\leo\system\vendor\lib\hw\gralloc.default.so')
TARGET='/system/vendor/lib/hw/gralloc.default.so'
def call(*args):
    result=subprocess.run(ADB+list(args),capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=120)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    return result.stdout.strip()
def parent(command):return call('shell',PARENT+command)
assert 'uid=0' in call('shell',BB+'id')
assert 'androidboot.serialno=695d ' in call('shell',BB+'cat /proc/cmdline')
assert call('shell',BB+'cat /leo-framework-internal')=='Validated internal Android partitions.'
assert '179:30' in parent('/system/bin/cat /proc/self/mountinfo')
new_sha=hashlib.sha256(SOURCE.read_bytes()).hexdigest()
old_sha=parent('/system/bin/sha256sum '+TARGET).split()[0]
assert new_sha!=old_sha, 'Already installed or build did not change'
backup=OUT/('gralloc-before-'+old_sha[:12]+'.so')
parent('/system/bin/cp '+TARGET+' /tmp/leo-gralloc-original.so')
call('pull','/tmp/leo-gralloc-original.so',str(backup))
assert hashlib.sha256(backup.read_bytes()).hexdigest()==old_sha
call('push',str(SOURCE),'/tmp/leo-gralloc-fixed.so')
assert call('shell',BB+'sha256sum /tmp/leo-gralloc-fixed.so').split()[0]==new_sha
for name in ('zygote','surfaceflinger','gralloc-2-0','hwcomposer-2-1'):
    parent('/system/bin/setprop ctl.stop '+name)
time.sleep(2)
parent('/system/bin/mount -o remount,rw /system')
try:
    commands='set -e; cp /tmp/leo-gralloc-fixed.so '+TARGET+'.new; chmod 644 '+TARGET+'.new; chown 0:2000 '+TARGET+'.new; restorecon '+TARGET+'.new; mv -f '+TARGET+'.new '+TARGET+'; sync'
    parent('/system/bin/sh -c '+shlex.quote(commands))
    assert parent('/system/bin/sha256sum '+TARGET).split()[0]==new_sha
finally:
    parent('/system/bin/mount -o remount,ro /system')
report={'target_serial':'695d','old_sha256':old_sha,'new_sha256':new_sha,
        'original_file_backup':str(backup),'system_remounted_read_only':True,
        'boot_written':False,'recovery_written':False,'userdata_formatted':False}
(OUT/'gralloc-fix-install.json').write_text(json.dumps(report,indent=2))
for name in ('hwcomposer-2-1','gralloc-2-0','surfaceflinger','zygote'):
    parent('/system/bin/setprop ctl.start '+name)
print(json.dumps(report,indent=2))
