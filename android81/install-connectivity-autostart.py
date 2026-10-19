"""Opt in to the existing trial at boot, preserving current Wi-Fi/Bluetooth preferences."""
from pathlib import Path
import subprocess,shlex,json,argparse
parser=argparse.ArgumentParser(description='Experimental only: boot connectivity caused a 900E episode; HCI remains unreliable.')
parser.add_argument('--experimental-enable',action='store_true')
args=parser.parse_args()
if not args.experimental_enable:
    parser.error('Autostart remains disabled. Resolve HCI/900E first; explicit --experimental-enable is required for a controlled trial.')
ROOT=Path(__file__).resolve().parent; ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*args): return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*args])],timeout=45).decode(errors='replace').replace('\r','')
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','sys.leo.connectivity.state').strip()=='ready'
assert 'service leo_connectivity_setup ' in native('/system/bin/cat','/system/etc/init/init.leo.connectivity.rc')
backup=ROOT/'output/connectivity-autostart-before'; backup.mkdir(exist_ok=True)
current=native('/system/bin/cat','/system/etc/leo-first-setup.sh')
if not (backup/'leo-first-setup.sh').exists(): (backup/'leo-first-setup.sh').write_bytes(current.encode())
remove={'cmd settings put global bluetooth_on 0','cmd settings put global ble_scan_always_enabled 0','sh /system/bin/svc bluetooth disable'}
updated='\n'.join(line for line in current.splitlines() if line.strip() not in remove)+'\n'
staged=ROOT/'output/connectivity-auto-setup.sh'; staged.write_bytes(updated.encode())
target='/system/etc/init/leo-connectivity-auto.rc'
existing=native('/system/bin/sh','-c','if [ -f '+target+' ]; then cat '+target+'; fi')
desired=(ROOT/'init.leo.connectivity-auto.rc').read_text()
assert not existing or existing.strip()==desired.strip(),'Existing autostart differs'
if not (backup/'state.json').exists(): (backup/'state.json').write_text(json.dumps({'previous_auto':native('/system/bin/getprop','persist.leo.connectivity.auto').strip()}))
for source,remote in [(ROOT/'init.leo.connectivity-auto.rc','/tmp/leo-connectivity-auto.rc'),(staged,'/tmp/leo-connectivity-auto-setup.sh')]:
    subprocess.run(ADB+['push',str(source),remote],check=True,stdout=subprocess.DEVNULL)
native('/system/bin/mount','-o','remount,rw','/system')
try:
    for source,dest in [('/tmp/leo-connectivity-auto.rc',target),('/tmp/leo-connectivity-auto-setup.sh','/system/etc/leo-first-setup.sh')]:
        native('/system/bin/cp',source,dest+'.new'); native('/system/bin/chmod','644',dest+'.new'); native('/system/bin/chown','0:0',dest+'.new'); native('/system/bin/mv',dest+'.new',dest); native('/system/bin/restorecon',dest)
finally:
    native('/system/bin/sync')
    try: native('/system/bin/mount','-o','remount,ro','/system')
    except subprocess.CalledProcessError: print('Read-only remount awaits planned framework reboot')
native('/system/bin/setprop','persist.leo.connectivity.auto','1')
print('Opt-in configured for next boot; current connection not restarted; Bluetooth no longer forcibly disabled by setup')
