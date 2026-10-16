"""Run native Android shell under stock recovery in a volatile /tmp chroot."""
from pathlib import Path
import hashlib,json,subprocess
here=Path(__file__).resolve().parent;out=here/'output'
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def call(*args):
    r=subprocess.run(adb+list(args),capture_output=True,text=True,timeout=30)
    if r.returncode:raise RuntimeError(r.stderr or r.stdout)
    return r.stdout.replace('\r','')
assert 'root-adb-v1' in call('shell','cat /tmp/leo-debug-init')
assert 'uid=0' in call('shell','id')
assert 'tmpfs /tmp tmpfs' in call('shell','cat /proc/mounts')
local=out/'android81-userspace-test.tar.gz'
meta=json.loads((out/'android81-userspace-test.json').read_text())
assert hashlib.sha256(local.read_bytes()).hexdigest()==meta['sha256']
remote='/tmp/leo-android81-runtime'
print(call('push',str(local),'/tmp/leo-android81-runtime.tar.gz'))
setup=' && '.join([
    '/system/bin/busybox mkdir -p '+remote,
    '/system/bin/busybox tar -xzf /tmp/leo-android81-runtime.tar.gz -C '+remote,
    '/system/bin/busybox mkdir -p '+remote+'/dev '+remote+'/tmp '+remote+'/proc',
    *['( [ -c '+remote+'/dev/'+name+' ] || /system/bin/busybox mknod -m 666 '+remote+'/dev/'+name+' c 1 '+str(minor)+' )'
      for name,minor in [('null',3),('zero',5),('random',8),('urandom',9)]]])
print(call('shell',setup))
print(call('shell','/system/bin/busybox mount -t proc -o ro,nosuid,nodev,noexec proc '+remote+'/proc'))
cmd="PATH=/system/bin LD_LIBRARY_PATH=/system/lib /system/bin/busybox chroot "+remote+" /system/bin/sh -c 'echo ANDROID81_NATIVE_SHELL_OK; /system/bin/id; /system/bin/toybox uname -a; /system/bin/toybox ls /system/lib/libc.so; echo ANDROID81_NATIVE_TEST_DONE'"
try:
    result=call('shell',cmd)
finally:
    print(call('shell','/system/bin/busybox umount '+remote+'/proc'))
(out/'android81-userspace-test.log').write_text(result)
print(result)
success='ANDROID81_NATIVE_SHELL_OK' in result and 'ANDROID81_NATIVE_TEST_DONE' in result and 'uid=0' in result
meta.update(native_shell_success=success,init_executed=False,persistent_partitions_mounted=False,recovery_preserved=True)
(out/'android81-userspace-result.json').write_text(json.dumps(meta,indent=2))
assert success,'Native runtime failed; inspect recorded output before further boot experiments'
