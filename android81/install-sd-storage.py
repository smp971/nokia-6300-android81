"""Install/restore vold's SD entry in the existing system recovery.fstab.

The diagnostic /sbin/recovery marker makes Oreo fs_mgr use this file. Only the
system configuration is replaced; no boot, recovery, partitioning or format.
Unmount/eject external storage before restoring. Reboot after installation.
"""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--restore',action='store_true');args=parser.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*a):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*a])],timeout=25).decode(errors='replace').strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/cat','/sys/class/block/mmcblk1/device/type')=='SD'
assert '/dev/leo-internal-system /system ext4' in native('/system/bin/cat','/proc/mounts'),'Internal Android only'
target='/system/etc/recovery.fstab'
assert not any(line.split()[1]==target for line in native('/system/bin/cat','/proc/mounts').splitlines()),'Remove temporary fstab bind mount before installation'
backup=root/'output/sd-storage-before';backup.mkdir(exist_ok=True)
manifest=backup/'manifest.json';saved=backup/'recovery.fstab'
current=native('/system/bin/sha256sum',target).split()[0]
if not manifest.exists():
    assert not args.restore
    native('/system/bin/cp',target,'/tmp/leo-sd-fstab-original')
    subprocess.run(adb+['pull','/tmp/leo-sd-fstab-original',str(saved)],check=True,capture_output=True)
    assert digest(saved)==current
    manifest.write_text(json.dumps(dict(before=current,after=None),indent=2))
record=json.loads(manifest.read_text());assert current in (record['before'],record['after']),'Unexpected installed fstab'
source=saved if args.restore else root/'leo-storage.fstab'
assert digest(saved)==record['before']
if not args.restore:
    assert [x for x in source.read_text().splitlines() if x and not x.startswith('#')][:2]==[x for x in saved.read_text().splitlines() if x and not x.startswith('#')],'Internal partition configuration differs'
    record['after']=digest(source);manifest.write_text(json.dumps(record,indent=2))
expected=digest(source)
subprocess.run(adb+['push',str(source),'/tmp/leo-sd-fstab-install'],check=True,capture_output=True)
assert native('/system/bin/sha256sum','/tmp/leo-sd-fstab-install').split()[0]==expected
native('/system/bin/mount','-o','remount,rw','/system')
try:
    native('/system/bin/cp','/tmp/leo-sd-fstab-install',target+'.leo-new')
    native('/system/bin/chmod','644',target+'.leo-new');native('/system/bin/chown','0:0',target+'.leo-new')
    native('/system/bin/mv',target+'.leo-new',target);native('/system/bin/restorecon',target)
    assert native('/system/bin/sha256sum',target).split()[0]==expected
finally:
    native('/system/bin/sync');native('/system/bin/mount','-o','remount,ro','/system')
print('SD configuration restored; reboot required' if args.restore else 'SD configuration installed; reboot required')
