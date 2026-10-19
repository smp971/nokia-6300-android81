"""Rejected graphics experiment; CPU wait stalled presentation on Leo.

Install/restore matching libgui with verified backup, without boot/recovery writes.
Keep the original library for normal use; see PERFORMANCE-AUDIT.md.
"""
import argparse,hashlib,json,re,shlex,subprocess,time
from pathlib import Path
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*a):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*a])],timeout=25).decode(errors='replace').strip()
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
target='/system/lib/libgui.so';backup=root/'output/consumer-fence-before';backup.mkdir(exist_ok=True);manifest=backup/'manifest.json'
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','ro.product.device')=='leo'
calls=re.findall(r'mCallState=(\d+)',native('/system/bin/dumpsys','telephony.registry'))
assert calls and set(calls)=={'0'},'Refusing graphics restart during a call or incoming ring'
assert 'ServiceRecord{' not in native('/system/bin/dumpsys','activity','services','ro.leo.visual.AlarmRingService')
current=native('/system/bin/sha256sum',target).split()[0];candidate=root/'output/libgui-leo-fence.so'
if not manifest.exists():
    assert not args.restore
    native('/system/bin/cp',target,'/tmp/leo-libgui-original.so');subprocess.run(adb+['pull','/tmp/leo-libgui-original.so',str(backup/'libgui.so')],check=True,capture_output=True)
    assert digest(backup/'libgui.so')==current
    manifest.write_text(json.dumps(dict(before=current,after=digest(candidate)),indent=2))
state=json.loads(manifest.read_text());assert current in (state['before'],state['after']),'Unexpected installed libgui'
source=backup/'libgui.so' if args.restore else candidate;expected=state['before'] if args.restore else state['after'];assert digest(source)==expected
subprocess.run(adb+['push',str(source),'/tmp/leo-libgui-candidate.so'],check=True,capture_output=True)
assert native('/system/bin/sha256sum','/tmp/leo-libgui-candidate.so').split()[0]==expected
native('/system/bin/setprop','persist.leo.adreno.cpu_wait','0')
for service in ['zygote','surfaceflinger']:native('/system/bin/setprop','ctl.stop',service)
time.sleep(1)
native('/system/bin/mount','-o','remount,rw','/system')
try:
    native('/system/bin/cp','/tmp/leo-libgui-candidate.so',target+'.leo-new');native('/system/bin/chmod','644',target+'.leo-new');native('/system/bin/chown','0:0',target+'.leo-new');native('/system/bin/restorecon',target+'.leo-new');native('/system/bin/mv',target+'.leo-new',target)
    assert native('/system/bin/sha256sum',target).split()[0]==expected
finally:
    try:
        native('/system/bin/sync')
        try:native('/system/bin/mount','-o','remount,ro','/system')
        except subprocess.CalledProcessError:print('Read-only remount deferred until old runtime mappings close; retry or reboot required')
    finally:
        native('/system/bin/setprop','sys.boot_completed','0')
        for service in ['surfaceflinger','zygote']:native('/system/bin/setprop','ctl.start',service)
print('Original libgui restored' if args.restore else 'libgui trial installed; CPU wait OFF until explicitly enabled')
