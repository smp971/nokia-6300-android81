"""Install/restore Leo boot artwork and optional system init hook. No boot/recovery flash."""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess,zipfile
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--enable-on-boot',action='store_true');parser.add_argument('--restore',action='store_true');args=parser.parse_args()
assert not(args.restore and args.enable_on_boot)
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*a):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*a])],timeout=20).decode(errors='replace').strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def exists(path):return native('/system/bin/sh','-c','if [ -f '+shlex.quote(path)+' ]; then printf yes; fi')=='yes'
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
backup=root/'output/bootanimation-before';backup.mkdir(exist_ok=True);manifest=backup/'manifest.json';records=json.loads(manifest.read_text()) if manifest.exists() else {}
files={'/system/media/bootanimation.zip':root/'output/bootanimation.zip'}
if args.enable_on_boot:files['/system/etc/init/leo-bootanimation.rc']=root/'leo-bootanimation.rc'
if args.restore:files={key:None for key in records}
else:
    with zipfile.ZipFile(root/'output/bootanimation.zip') as z:
        assert z.read('desc.txt').decode().startswith('240 320 6\n')
        assert len(z.namelist())==11 and all(f.compress_type==zipfile.ZIP_STORED for f in z.infolist())
for n,(target,source) in enumerate(files.items()):
    current=native('/system/bin/sha256sum',target).split()[0] if exists(target) else None
    if target not in records:
        assert not args.restore
        saved=backup/(str(len(records))+'-'+Path(target).name)
        if current:
            native('/system/bin/cp',target,'/tmp/leo-bootanim-original');subprocess.run(adb+['pull','/tmp/leo-bootanim-original',str(saved)],check=True,capture_output=True);assert digest(saved)==current
        records[target]=dict(before=current,saved=str(saved),after=digest(source))
    record=records[target];assert current in (record['before'],record['after']),'Unexpected file: '+target
    if not args.restore:record['after']=digest(source)
    chosen=Path(record['saved']) if args.restore and record['before'] else source
    if chosen:
        expected=record['before'] if args.restore else record['after'];assert digest(chosen)==expected
        staged='/tmp/leo-bootanim-'+str(n);subprocess.run(adb+['push',str(chosen),staged],check=True,capture_output=True)
        assert native('/system/bin/sha256sum',staged).split()[0]==expected
manifest.write_text(json.dumps(records,indent=2))
native('/system/bin/mount','-o','remount,rw','/system')
try:
    for n,(target,source) in enumerate(files.items()):
        record=records[target]
        if args.restore and record['before'] is None:native('/system/bin/rm','-f',target);continue
        staged='/tmp/leo-bootanim-'+str(n);native('/system/bin/cp',staged,target+'.leo-new');native('/system/bin/chmod','644',target+'.leo-new');native('/system/bin/chown','0:0',target+'.leo-new');native('/system/bin/restorecon',target+'.leo-new');native('/system/bin/mv',target+'.leo-new',target)
        expected=record['before'] if args.restore else record['after'];assert native('/system/bin/sha256sum',target).split()[0]==expected
finally:native('/system/bin/sync');native('/system/bin/mount','-o','remount,ro','/system')
if args.restore:
    native('/system/bin/setprop','service.bootanim.exit','1');native('/system/bin/setprop','ctl.stop','bootanim');native('/system/bin/setprop','debug.sf.nobootanimation','1')
print('Boot animation restored' if args.restore else 'Leo assets installed'+('; init hook activates after reboot' if args.enable_on_boot else '; manual trial ready'))
