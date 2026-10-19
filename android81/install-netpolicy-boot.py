"""Install or restore the four matching services artifacts on the validated 695d target."""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess
ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--restore',action='store_true'); args=parser.parse_args()
def native(*command): return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*command])],timeout=60).decode(errors='replace').strip()
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def remote_hash(path): return native('/system/bin/sha256sum',path).split()[0]
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/uname','-r')=='4.9.240-perf-g4f9e270fd77f'
source=ROOT/'output/netpolicy-runtime'; backup=ROOT/'output/netpolicy-device-before'; backup.mkdir(exist_ok=True)
paths={name:'/system/framework/'+('' if name.endswith('.jar') else 'oat/arm/')+name for name in ('services.jar','services.art','services.odex','services.vdex')}
state=backup/'manifest.json'
if not args.restore:
    prerequisites=json.loads((source/'boot-prerequisites.json').read_text())
    for path,expected in prerequisites.items(): assert remote_hash('/'+path)==expected,'Boot artifact mismatch: '+path
    assert all((source/name).stat().st_size>0 for name in paths)
    after={name:digest(source/name) for name in paths}
    if not state.exists():
        before={}
        for i,(name,target) in enumerate(paths.items()):
            staged='/tmp/leo-services-backup-'+str(i); before[name]=remote_hash(target)
            native('/system/bin/cp',target,staged)
            subprocess.run(ADB+['pull',staged,str(backup/name)],check=True,stdout=subprocess.DEVNULL)
            assert digest(backup/name)==before[name]
            native('/system/bin/rm',staged)
        state.write_text(json.dumps(dict(before=before,after=after),indent=2))
    recorded=json.loads(state.read_text())
    assert all(remote_hash(paths[name]) in (recorded['before'][name],recorded['after'][name]) for name in paths),'Unrecognized current framework'
    recorded['after']=after; state.write_text(json.dumps(recorded,indent=2))
    chosen=source
else:
    recorded=json.loads(state.read_text()); chosen=backup
    assert all(remote_hash(paths[name]) in (recorded['before'][name],recorded['after'][name]) for name in paths),'Unrecognized current framework'
expected=recorded['before'] if args.restore else recorded['after']
for i,name in enumerate(paths):
    assert digest(chosen/name)==expected[name]
    subprocess.run(ADB+['push',str(chosen/name),'/tmp/leo-services-stage-'+str(i)],check=True,stdout=subprocess.DEVNULL)
def replace(staged,target):
    native('/system/bin/cp',staged,target+'.leo-new'); native('/system/bin/chmod','644',target+'.leo-new')
    native('/system/bin/chown','0:0',target+'.leo-new'); native('/system/bin/restorecon',target+'.leo-new')
    native('/system/bin/mv',target+'.leo-new',target); native('/system/bin/restorecon',target)
native('/system/bin/mount','-o','remount,rw','/system')
try:
    for i,(name,target) in enumerate(paths.items()): replace('/tmp/leo-services-stage-'+str(i),target)
    assert all(remote_hash(paths[name])==expected[name] for name in paths)
except Exception:
    if not args.restore:
        for i,(name,target) in enumerate(paths.items()):
            subprocess.run(ADB+['push',str(backup/name),'/tmp/leo-services-rollback-'+str(i)],check=True,stdout=subprocess.DEVNULL)
            replace('/tmp/leo-services-rollback-'+str(i),target)
    raise
finally:
    native('/system/bin/sync')
    try: native('/system/bin/mount','-o','remount,ro','/system')
    except subprocess.CalledProcessError:
        # ART can retain writable FDs to replaced, now-unlinked preopt files.
        print('RO remount deferred: live ART holds replaced files; reboot is required to release them')
for i in range(len(paths)): native('/system/bin/rm','/tmp/leo-services-stage-'+str(i))
read_only=' /system ext4 ro,' in native('/system/bin/cat','/proc/mounts')
print('Restored' if args.restore else 'Installed','matching services artifacts; system_read_only='+str(read_only)+'; reboot required; original copies retained locally')
