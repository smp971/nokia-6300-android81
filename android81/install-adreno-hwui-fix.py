"""Back up and atomically update HWUI for the validated legacy A304 driver."""
from pathlib import Path
import hashlib
import json
import shlex
import subprocess
import time

here=Path(__file__).resolve().parent
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def call(*args):
    r=subprocess.run(adb+list(args),capture_output=True,text=True,
        encoding='utf-8',errors='replace',timeout=45)
    if r.returncode: raise RuntimeError(r.stdout+r.stderr)
    return r.stdout.strip()
def shell(command):
    return call('shell','/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(command))
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('cat /leo-framework-internal')=='Validated internal Android partitions.'
source=here/'output/libhwui-adreno.so'
new=hashlib.sha256(source.read_bytes()).hexdigest()
old=shell('sha256sum /system/lib/libhwui.so').split()[0]
if old==new: print('Already installed'); raise SystemExit(0)
assert old=='f90730874133eff7d6b6975245f75b164af6eadaf9996fa5920be99ca2c11b1b'
shell('cp /system/lib/libhwui.so /tmp/leo-hwui-original.so')
backup=here/'output/libhwui-before-adreno.so'
call('pull','/tmp/leo-hwui-original.so',str(backup))
assert hashlib.sha256(backup.read_bytes()).hexdigest()==old
call('push',str(source),'/tmp/leo-hwui-fixed.so')
assert shell('sha256sum /tmp/leo-hwui-fixed.so').split()[0]==new
shell('setprop ctl.stop zygote')
time.sleep(2)
try:
    shell("set -e; mount -o remount,rw /system; trap 'mount -o remount,ro /system' EXIT; "
          'cp /tmp/leo-hwui-original.so /system/vendor/lib/leo-adreno/libhwui-original.so; '
          'chmod 644 /system/vendor/lib/leo-adreno/libhwui-original.so; '
          'cp /tmp/leo-hwui-fixed.so /system/lib/libhwui.so.new; '
          'chmod 644 /system/lib/libhwui.so.new; chown 0:0 /system/lib/libhwui.so.new; '
          'restorecon /system/lib/libhwui.so.new; mv -f /system/lib/libhwui.so.new /system/lib/libhwui.so; sync')
    assert shell('sha256sum /system/lib/libhwui.so').split()[0]==new
    shell('setprop persist.leo.adreno.hwui_finish 1; setprop sys.boot_completed 0')
finally:
    shell('setprop ctl.start zygote')
report={'old_sha256':old,'new_sha256':new,'backup':str(backup),'boot_recovery_written':False}
(here/'output/adreno-hwui-fix.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
