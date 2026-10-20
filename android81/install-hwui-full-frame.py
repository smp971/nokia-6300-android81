"""Install the reversible full-frame HWUI configuration; no restart or flashing.

Applies the property immediately for newly initialized EGL renderers and at
future boots. Existing app processes may retain their earlier swap behavior.
--undo removes only our owned init file and restores the upstream property
default (empty). It does not restart apps, graphics, audio, init or the phone.
"""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--undo',action='store_true')
args=parser.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
source=root/'device/nokia/leo/init.leo.hwui.rc'
destination='/system/etc/init/leo-hwui-full-frame.rc'
expected=hashlib.sha256(source.read_bytes()).hexdigest()

def native(*command):
    return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*command])],timeout=25).decode('utf-8',errors='replace').strip()

assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/cat','/leo-framework-internal')=='Validated internal Android partitions.'
exists=native('/system/bin/sh','-c','test -f '+shlex.quote(destination)+' && echo yes || true')=='yes'
if exists:assert native('/system/bin/sha256sum',destination).split()[0]==expected,'Unexpected existing configuration; retained without overwrite'
before=native('/system/bin/getprop','debug.hwui.use_partial_updates')
if not args.undo:
    subprocess.run(adb+['push',str(source),'/tmp/leo-hwui-full-frame.rc'],check=True,timeout=25)
    assert native('/system/bin/sha256sum','/tmp/leo-hwui-full-frame.rc').split()[0]==expected
native('/system/bin/mount','-o','remount,rw','/system')
try:
    if args.undo:
        if exists:native('/system/bin/rm',destination)
    else:
        native('/system/bin/cp','/tmp/leo-hwui-full-frame.rc',destination+'.new')
        native('/system/bin/chown','0:0',destination+'.new')
        native('/system/bin/chmod','644',destination+'.new')
        native('/system/bin/restorecon',destination+'.new')
        native('/system/bin/mv',destination+'.new',destination)
        assert native('/system/bin/sha256sum',destination).split()[0]==expected
    native('/system/bin/sync')
finally:native('/system/bin/mount','-o','remount,ro','/system')
native('/system/bin/setprop','debug.hwui.use_partial_updates','' if args.undo else 'false')
report={'before':before,'after':native('/system/bin/getprop','debug.hwui.use_partial_updates'),'boot_config_installed':not args.undo,'framework_restarted':False,'boot_recovery_written':False}
(root/'output/hwui-full-frame-install.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
