"""Install/restore the Leo audio policy and initialization fix; no partition flashing."""
from pathlib import Path
import argparse,hashlib,json,re,shlex,subprocess
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*a):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*a])],timeout=25).decode(errors='replace').strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def exists(path):return native('/system/bin/sh','-c','if [ -f '+shlex.quote(path)+' ]; then echo yes; fi')=='yes'
assert 'androidboot.serialno=695d ' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/uname','-r')=='4.9.240-perf-g4f9e270fd77f'
calls=re.findall(r'mCallState=(\d+)',native('/system/bin/dumpsys','telephony.registry'))
assert calls and set(calls)=={'0'},'Audio restart is refused during a call'
assert '/dev/leo-internal-system /system ext4' in native('/system/bin/cat','/proc/mounts')
backup=root/'output/audio-fix-before';backup.mkdir(exist_ok=True);manifest=backup/'manifest.json'
records=json.loads(manifest.read_text()) if manifest.exists() else {}
files={'/system/etc/audio_policy.conf':(root/'device/nokia/leo/audio_policy.conf',644),
       '/system/vendor/leo-audio/start.sh':(root/'audio-runtime/start.sh',755),
       '/system/etc/init/init.leo.audio.rc':(root/'audio-runtime/init.leo.audio.rc',644),
       '/system/etc/mixer_paths.xml':(root/'output/mixer_paths-earpiece.xml',644),
       '/system/vendor/leo-radio/start.sh':(root/'radio-runtime/start.sh',755)}
if args.restore:assert records,'No saved original files'
for i,(target,(source,mode)) in enumerate(files.items()):
    current=native('/system/bin/sha256sum',target).split()[0] if exists(target) else None
    if target not in records:
        assert not args.restore
        saved=backup/(str(i)+'-'+Path(target).name)
        if current:
            native('/system/bin/cp',target,'/tmp/leo-audio-fix-original');subprocess.run(adb+['pull','/tmp/leo-audio-fix-original',str(saved)],check=True,capture_output=True);assert digest(saved)==current
        records[target]=dict(before=current,saved=str(saved),after=None)
    record=records[target];assert current in (record['before'],record['after']),'Unexpected file: '+target
    chosen=Path(record['saved']) if args.restore else source
    if args.restore and record['before'] is None:continue
    expected=record['before'] if args.restore else digest(chosen)
    assert digest(chosen)==expected
    if not args.restore:record['after']=expected
    subprocess.run(adb+['push',str(chosen),'/tmp/leo-audio-fix-'+str(i)],check=True,capture_output=True)
    assert native('/system/bin/sha256sum','/tmp/leo-audio-fix-'+str(i)).split()[0]==expected
manifest.write_text(json.dumps(records,indent=2))
native('/system/bin/setprop','persist.leo.audio.autoload','0')
native('/system/bin/setprop','persist.leo.audio.validated','0')
native('/system/bin/rm','-f','/data/leo-audio-trace/early-arm')
native('/system/bin/setprop','ctl.stop','audioserver')
native('/system/bin/setprop','ctl.stop','audio-hal-2-0')
try:
    native('/system/bin/mount','-o','remount,rw','/system')
    try:
        for i,(target,(source,mode)) in enumerate(files.items()):
            record=records[target]
            if args.restore and record['before'] is None:native('/system/bin/rm','-f',target);continue
            native('/system/bin/cp','/tmp/leo-audio-fix-'+str(i),target+'.leo-new')
            native('/system/bin/chmod',str(mode),target+'.leo-new');native('/system/bin/chown','0:0',target+'.leo-new')
            native('/system/bin/mv',target+'.leo-new',target);native('/system/bin/restorecon',target)
            assert native('/system/bin/sha256sum',target).split()[0]==record['before' if args.restore else 'after']
    finally:native('/system/bin/sync');native('/system/bin/mount','-o','remount,ro','/system')
finally:
    native('/system/bin/setprop','ctl.start','audio-hal-2-0')
    native('/system/bin/setprop','ctl.start','audioserver')
print('Audio files restored; autoload disabled' if args.restore else 'Audio policy/setup files installed; boot activation remains disabled until validated')
