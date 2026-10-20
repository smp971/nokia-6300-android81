"""Install Leo framework resource values after checking all resource IDs.

Only framework-res.apk is replaced. Framework JARs and their ART companions are
untouched. The current APK is verified and retained in ignored output.
"""
from pathlib import Path
import argparse
import hashlib
import re
import shlex
import subprocess
import zipfile

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--profile',choices=['connectivity','headset'],default='connectivity')
args=parser.parse_args()
ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*command):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*command])],timeout=45).decode('utf-8',errors='replace').strip()
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def resources(path):
    windows=path.resolve().as_posix()
    linux='/mnt/'+windows[0].lower()+windows[2:]
    dump=subprocess.check_output(['wsl','-d','kaios','--','/root/android81-leo/src/out/host/linux-x86/bin/aapt',
                                  'dump','resources',linux],timeout=45).decode('utf-8',errors='strict')
    entries=re.findall(r'spec resource (0x[0-9a-f]+) (android:[^\s]+): flags=',dump)
    assert len(entries)>5000,'Incomplete resource table'
    return dict(entries)
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','sys.boot_completed')=='1'
destination='/system/framework/framework-res.apk'
folder=ROOT/('output/'+args.profile+'-repair')
folder.mkdir(exist_ok=True)
expected=native('/system/bin/sha256sum',destination).split()[0]
before=folder/('framework-res-before-'+expected+'.apk')
if not before.exists():
    native('/system/bin/cp',destination,'/tmp/leo-framework-res-before.apk')
    subprocess.run(ADB+['pull','/tmp/leo-framework-res-before.apk',str(before)],check=True,capture_output=True)
assert digest(before)==expected,'Current framework resources differ from the retained baseline'
after=ROOT/'output/framework-res-connectivity.apk'
assert resources(before)==resources(after),'Resource ID/name mapping changed'
with zipfile.ZipFile(before) as old, zipfile.ZipFile(after) as new:
    def content(archive): return {name:hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist() if not name.startswith('META-INF/')}
    a,b=content(old),content(new)
    assert a.keys()==b.keys(),'Unexpected APK entry changes'
    changed={name for name in a if a[name]!=b[name]}
    assert changed=={'resources.arsc'},changed
subprocess.run(ADB+['push',str(after),'/tmp/leo-framework-res-connectivity.apk'],check=True,capture_output=True)
assert native('/system/bin/sha256sum','/tmp/leo-framework-res-connectivity.apk').split()[0]==digest(after)
native('/system/bin/mount','-o','remount,rw','/system')
try:
    native('/system/bin/cp','/tmp/leo-framework-res-connectivity.apk',destination+'.new')
    native('/system/bin/chmod','644',destination+'.new')
    native('/system/bin/chown','0:0',destination+'.new')
    native('/system/bin/mv',destination+'.new',destination)
    native('/system/bin/restorecon',destination)
finally:
    native('/system/bin/sync')
    try:
        native('/system/bin/mount','-o','remount,ro','/system')
    except subprocess.CalledProcessError:
        print('Read-only remount is busy with live framework mappings; reboot immediately after staging')
assert native('/system/bin/sha256sum',destination).split()[0]==digest(after)
print('Only resource values changed; all IDs preserved; old APK retained; reboot required')
