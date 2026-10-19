"""Install/restore the Leo local WMA decoder and opt-in MTP experiment."""
from pathlib import Path
import argparse,hashlib,json,re,shlex,subprocess
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*a):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*a])],timeout=25).decode(errors='replace').strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def exists(p):return native('/system/bin/sh','-c','if [ -f '+shlex.quote(p)+' ]; then echo yes; fi')=='yes'
assert 'androidboot.serialno=695d ' in native('/system/bin/cat','/proc/cmdline')
assert '/dev/leo-internal-system /system ext4' in native('/system/bin/cat','/proc/mounts')
calls=re.findall(r'mCallState=(\d+)',native('/system/bin/dumpsys','telephony.registry'))
assert calls and set(calls)=={'0'},'Refused during a call'
backup=root/'output/media-runtime-before';backup.mkdir(exist_ok=True);manifest=backup/'manifest.json'
records=json.loads(manifest.read_text()) if manifest.exists() else {}
files={'/system/bin/leo-wma-decode':(root/'output/leo-wma-decode',755),
       '/system/etc/NOTICE.leo-wma':(root/'output/leo-wma-LICENSE.txt',644),
       '/system/etc/leo-usb-mtp.sh':(root/'usb-mtp.sh',755),
       '/system/etc/init/init.leo.mtp.rc':(root/'init.leo.mtp.rc',644)}
if args.restore:assert records,'No saved installation'
for i,(target,(source,mode)) in enumerate(files.items()):
    current=native('/system/bin/sha256sum',target).split()[0] if exists(target) else None
    if target not in records:
        assert not args.restore
        saved=backup/(str(i)+'-'+Path(target).name)
        if current:
            native('/system/bin/cp',target,'/tmp/leo-media-original')
            subprocess.run(adb+['pull','/tmp/leo-media-original',str(saved)],check=True,capture_output=True)
            assert digest(saved)==current
        records[target]=dict(before=current,saved=str(saved),after=None)
    record=records[target];assert current in (record['before'],record['after']),'Unexpected file: '+target
    chosen=Path(record['saved']) if args.restore else source
    if args.restore and record['before'] is None:continue
    expected=record['before'] if args.restore else digest(chosen);assert digest(chosen)==expected
    if not args.restore:record['after']=expected
    subprocess.run(adb+['push',str(chosen),'/tmp/leo-media-'+str(i)],check=True,capture_output=True)
    assert native('/system/bin/sha256sum','/tmp/leo-media-'+str(i)).split()[0]==expected
manifest.write_text(json.dumps(records,indent=2))
native('/system/bin/mount','-o','remount,rw','/system')
try:
    for i,(target,(source,mode)) in enumerate(files.items()):
        record=records[target]
        if args.restore and record['before'] is None:native('/system/bin/rm','-f',target);continue
        native('/system/bin/cp','/tmp/leo-media-'+str(i),target+'.leo-new')
        native('/system/bin/chmod',str(mode),target+'.leo-new');native('/system/bin/chown','0:0',target+'.leo-new')
        native('/system/bin/mv',target+'.leo-new',target);native('/system/bin/restorecon',target)
        assert native('/system/bin/sha256sum',target).split()[0]==record['before' if args.restore else 'after']
finally:native('/system/bin/sync');native('/system/bin/mount','-o','remount,ro','/system')
print('Media files restored; reboot to unload init hooks' if args.restore else 'WMA decoder installed; experimental MTP service activates after reboot, transfer remains off')
