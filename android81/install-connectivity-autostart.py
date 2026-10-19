"""Install early WCNSS initialization with a disarmed first boot trial."""
from pathlib import Path
import argparse
import json
import shlex
import subprocess

parser=argparse.ArgumentParser(description=__doc__)
mode=parser.add_mutually_exclusive_group(required=True)
mode.add_argument('--prepare-trial', action='store_true')
mode.add_argument('--accept-boot', action='store_true', help='Rearm only after verifying the early boot trial')
args=parser.parse_args()
ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']

def native(*command):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*command])],
                                   timeout=45).decode('utf-8',errors='replace').replace('\r','')

assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','sys.leo.connectivity.state').strip()=='ready'
if args.accept_boot:
    assert native('/system/bin/getprop','sys.boot_completed').strip()=='1'
    assert native('/system/bin/getprop','persist.leo.connectivity.auto').strip()=='0'
    assert 'exec_start leo_connectivity_setup' in native('/system/bin/cat','/system/etc/init/leo-connectivity-auto.rc')
    native('/system/bin/setprop','persist.leo.connectivity.validated','1')
    native('/system/bin/setprop','persist.leo.connectivity.auto','1')
    print('Verified boot accepted; subsequent successful boots rearm connectivity')
    raise SystemExit(0)

assert 'service hostapd ' in native('/system/bin/cat','/system/etc/init/init.leo.connectivity.rc')
backup=ROOT/'output/connectivity-early-before'
backup.mkdir(exist_ok=True)
current=native('/system/bin/cat','/system/etc/leo-first-setup.sh')
old='cmd settings put global bluetooth_on 0\ncmd settings put global ble_scan_always_enabled 0\nsh /system/bin/svc bluetooth disable'
new='# Preserve saved Bluetooth state only when early hardware initialization succeeded.\nif [ "$(getprop sys.leo.connectivity.state)" != ready ]; then\n    '+old.replace('\n','\n    ')+'\nfi'
if old in current:
    assert current.count(old)==1
    current=current.replace(old,new)
elif 'if [ "$(getprop sys.leo.connectivity.state)" != ready ]' not in current:
    current+='\n'+new+'\n'
current=current.replace('# Keep Bluetooth off until HCI initialization is reliable after boot.\n','')
destinations={
    '/system/etc/init/leo-connectivity-auto.rc':(ROOT/'init.leo.connectivity-auto.rc').read_text(encoding='utf-8'),
    '/system/etc/leo-first-setup.sh':current,
}
if not (backup/'state.json').exists():
    (backup/'state.json').write_text(json.dumps({key:native('/system/bin/getprop',key).strip() for key in
        ['persist.leo.connectivity.auto','persist.leo.connectivity.validated']}),encoding='utf-8')
for index,(destination,value) in enumerate(destinations.items()):
    saved=backup/Path(destination).name
    before=native('/system/bin/cat',destination)
    if not saved.exists(): saved.write_text(before,encoding='utf-8')
    path=backup/('staged-'+Path(destination).name)
    path.write_bytes(value.encode('utf-8'))
    subprocess.run(ADB+['push',str(path),'/tmp/leo-connectivity-early-'+str(index)],check=True,capture_output=True)
native('/system/bin/mount','-o','remount,rw','/system')
try:
    for index,destination in enumerate(destinations):
        native('/system/bin/cp','/tmp/leo-connectivity-early-'+str(index),destination+'.new')
        native('/system/bin/chmod','644',destination+'.new')
        native('/system/bin/chown','0:0',destination+'.new')
        native('/system/bin/mv',destination+'.new',destination)
        native('/system/bin/restorecon',destination)
finally:
    native('/system/bin/sync')
    native('/system/bin/mount','-o','remount,ro','/system')
native('/system/bin/setprop','persist.leo.connectivity.validated','0')
native('/system/bin/setprop','persist.leo.connectivity.auto','0')
print('Early initialization staged; arm immediately before reboot (old init may still have the late trigger); system read-only')
