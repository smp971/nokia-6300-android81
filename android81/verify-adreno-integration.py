"""Validate the active graphics stack; --accept cancels rollback for this boot."""
from pathlib import Path
import argparse
import json
import shlex
import subprocess
import time

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--accept',action='store_true')
args=parser.parse_args()
here=Path(__file__).resolve().parent
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def shell(command):
    r=subprocess.run(adb+['shell','/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(command)],
        capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=15)
    if r.returncode: raise RuntimeError(r.stdout+r.stderr)
    return r.stdout.replace('\r','').strip()
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
deadline=time.monotonic()+180
while True:
    try:
        boot=shell('getprop sys.boot_completed')
        user=shell('dumpsys user')
    except (RuntimeError, subprocess.TimeoutExpired):
        if time.monotonic()>deadline: raise
        time.sleep(5)
        continue
    if boot=='1' and 'State: RUNNING_UNLOCKED' in user: break
    if time.monotonic()>deadline: raise SystemExit('Boot/user unlock did not finish')
    time.sleep(5)
surface=shell('dumpsys SurfaceFlinger')
assert 'GLES: Qualcomm, Adreno (TM) 304' in surface
assert 'Google SwiftShader' not in surface
assert ' /system ext4 ro,' in shell('cat /proc/mounts')
for service in ('surfaceflinger','zygote','gralloc-2-0','hwcomposer-2-1'):
    assert shell('getprop init.svc.'+service)=='running',service
focus=shell('dumpsys window windows | grep mCurrentFocus')
assert 'Application Not Responding' not in focus,focus
assert 'ro.leo.shell' in focus or 'com.android.settings' in focus,focus
boot_id=shell('cat /proc/sys/kernel/random/boot_id')
report={'boot_id':boot_id,'boot_completed':True,'user_unlocked':True,
        'renderer':next(line for line in surface.splitlines() if line.startswith('GLES:')),
        'focus':focus,'system_read_only':True,'accepted':args.accept,
        'boot_recovery_written':False}
if args.accept:
    shell('cat /proc/sys/kernel/random/boot_id > /data/local/tmp/leo-adreno-accepted; sync')
    assert shell('cat /data/local/tmp/leo-adreno-accepted')==boot_id
    shell('setprop persist.leo.adreno.validated 1')
    assert shell('getprop persist.leo.adreno.validated')=='1'
(here/'output/adreno-integration-verified.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
