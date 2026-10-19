"""Stage private camera dependencies and run a hardware-free linker sandbox."""
from pathlib import Path
import hashlib,json,shlex,subprocess,tarfile
root=Path(__file__).resolve().parent
stock=root.parent/'analysis/system';out=root/'output'
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*args):
    return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*args])],timeout=25).decode(errors='replace').strip()
assert 'androidboot.serialno=695d ' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','sys.boot_completed')=='1'
assert 'mCallState=1' not in (calls:=native('/system/bin/dumpsys','telephony.registry')) and 'mCallState=2' not in calls
audit=json.loads((out/'camera-port-audit.json').read_text())
files={}
for row in audit['libraries']:
    name=row['name']
    if not name.endswith('.so'):continue
    assert '/' not in name and '\\' not in name
    source=next((p for p in [stock/'lib'/name,stock/'vendor/lib'/name,stock/'lib/hw'/name] if p.is_file()),None)
    assert source and not source.is_symlink(),name
    files[name]=source
files['probe']=out/'leo-camera-linker-probe'
assert files['probe'].is_file()
destination='/data/local/tmp/leo-camera-probe'
exists=native('/system/bin/sh','-c','test -d '+destination+' && echo yes || true')=='yes'
if exists:assert native('/system/bin/cat',destination+'/.owner')=='Leo camera linker sandbox v1'
native('/system/bin/mkdir','-p',destination)
native('/system/bin/sh','-c',"printf 'Leo camera linker sandbox v1' > "+destination+'/.owner; chmod 755 '+destination)
bundle=out/'camera-linker-probe.tar'
digests={}
with tarfile.open(bundle,'w') as archive:
    for name,source in files.items():
        digests[name]=hashlib.sha256(source.read_bytes()).hexdigest()
        info=archive.gettarinfo(str(source),arcname=name);info.uid=info.gid=0;info.mode=0o755 if name=='probe' else 0o644
        with source.open('rb') as f:archive.addfile(info,f)
# Stage the small private archive in the existing early-adbd RAM mount.
staging='/tmp/leo-camera-linker-probe.tar'
subprocess.run(adb+['push',str(bundle),staging],check=True,timeout=45)
try:
    assert native('/system/bin/sha256sum',staging).split()[0]==hashlib.sha256(bundle.read_bytes()).hexdigest()
    native('/system/bin/tar','-xf',staging,'-C',destination)
finally:
    native('/system/bin/rm','-f',staging)
for name,digest in digests.items():assert native('/system/bin/sha256sum',destination+'/'+name).split()[0]==digest,name
before=dict(audio=native('/system/bin/getprop','sys.leo.audio.state'),boot=native('/system/bin/cat','/proc/sys/kernel/random/boot_id'))
env='LD_LIBRARY_PATH='+destination+':/vendor/lib:/system/lib'
result=subprocess.run(adb+['shell',shlex.join(['/sbin/leo-framework-exec','/system/bin/env',env,destination+'/probe'])],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=20)
report=dict(returncode=result.returncode,stdout=result.stdout,stderr=result.stderr,hashes=digests,
            hardware_methods_called=False,namespace_sandbox=True,before=before,
            after=dict(audio=native('/system/bin/getprop','sys.leo.audio.state'),boot=native('/system/bin/cat','/proc/sys/kernel/random/boot_id')))
(out/'camera-linker-result.json').write_text(json.dumps(report,indent=2))
print(result.stdout);print(result.stderr);print('Phone state unchanged:',report['before']==report['after'])
