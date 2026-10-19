"""Install Leo system Settings with an explicit privileged permission allowlist.

Retain the existing package identity/data; install a same-signed system base and
an explicit allowlist for hotspot, SIM, clock and secure settings. Reboot
activates newly requested privileged permissions.
"""
from pathlib import Path
import hashlib
import json
import shlex
import subprocess

ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*command):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*command])],timeout=45).decode('utf-8',errors='replace').strip()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
paths=subprocess.check_output(['python',str(ROOT/'framework-command.py'),'/system/bin/sh',
                              '/system/bin/pm','path','ro.leo.visual'],timeout=45).decode('utf-8').replace('\r','').splitlines()
assert len(paths)==1 and paths[0].startswith('package:')
apk=ROOT/'output/leo-visual.apk'
expected=hashlib.sha256(apk.read_bytes()).hexdigest()
# Install the very same APK as a data update first; Android verifies signer compatibility.
assert native('/system/bin/sha256sum',paths[0][8:]).split()[0]==expected,'Install the compiled APK with pm install -r first'
files={
    '/system/priv-app/LeoVisual/LeoVisual.apk':apk,
    '/system/etc/permissions/privapp-permissions-leo-visual.xml':ROOT/'visual-shell/privapp-permissions-leo-visual.xml',
}
backup=ROOT/'output/visual-system-settings-before'
backup.mkdir(exist_ok=True)
manifest=backup/'installed.json'
previous=json.loads(manifest.read_text(encoding='utf-8')) if manifest.exists() else {}
digests={}
for index,(destination,source) in enumerate(files.items()):
    digest=hashlib.sha256(source.read_bytes()).hexdigest(); digests[destination]=digest
    exists=native('/system/bin/sh','-c','test -f '+shlex.quote(destination)+' && echo yes || true')=='yes'
    if exists:
        before=native('/system/bin/sha256sum',destination).split()[0]
        assert before in {digest,previous.get(destination)},'Unexpected existing system file: '+destination
        saved=backup/(str(index)+'-'+Path(destination).name)
        if not saved.exists():
            native('/system/bin/cp',destination,'/tmp/leo-settings-before-'+str(index))
            subprocess.run(ADB+['pull','/tmp/leo-settings-before-'+str(index),str(saved)],check=True,capture_output=True)
            assert hashlib.sha256(saved.read_bytes()).hexdigest()==before
    subprocess.run(ADB+['push',str(source),'/tmp/leo-system-settings-'+str(index)],check=True,capture_output=True)
    assert native('/system/bin/sha256sum','/tmp/leo-system-settings-'+str(index)).split()[0]==digest
native('/system/bin/mount','-o','remount,rw','/system')
try:
    for index,destination in enumerate(files):
        native('/system/bin/mkdir','-p',str(Path(destination).parent).replace('\\','/'))
        native('/system/bin/cp','/tmp/leo-system-settings-'+str(index),destination+'.new')
        native('/system/bin/chmod','644',destination+'.new')
        native('/system/bin/chown','0:0',destination+'.new')
        native('/system/bin/mv',destination+'.new',destination)
        native('/system/bin/restorecon','-R',destination.rsplit('/',1)[0])
finally:
    native('/system/bin/sync')
    native('/system/bin/mount','-o','remount,ro','/system')
for destination,digest in digests.items(): assert native('/system/bin/sha256sum',destination).split()[0]==digest
manifest.write_text(json.dumps(digests,indent=2),encoding='utf-8')
print('Leo system settings base synchronized; data retained; newly added privileges activate after reboot; system read-only')
