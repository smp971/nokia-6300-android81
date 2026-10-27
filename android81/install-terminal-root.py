"""Owner-authorized root broker for Leo only. Leaves the stock su unchanged.

Installs ordinary 0755 executables and an init service, never a world-accessible
setuid shell. The root daemon validates SO_PEERCRED against the current Leo UID
stored in a root-owned file. Does not restart Android or write boot/recovery.
"""
from pathlib import Path
import hashlib,json,re,shlex,subprocess
root=Path(__file__).resolve().parent
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*cmd):
    return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*cmd])],timeout=25).decode('utf-8',errors='replace').strip()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/cat','/leo-framework-internal')=='Validated internal Android partitions.'
package=native('/system/bin/dumpsys','package','ro.leo.visual')
found=re.search(r'^\s*userId=(\d+)\s*$',package,re.M);assert found
uid=int(found.group(1));assert 10000<=uid<20000
uidfile=root/'output/leo-terminal-root.uid';uidfile.write_bytes((str(uid)+'\n').encode())
files={'/system/bin/leo-terminal-root':(root/'output/leo-terminal-root',0o755),'/system/bin/leo-su':(root/'output/leo-terminal-root',0o755),'/system/etc/init/leo-terminal-root.rc':(root/'device/nokia/leo/init.leo.terminal-root.rc',0o644),'/data/system/leo-terminal-root.uid':(uidfile,0o600)}
reportfile=root/'output/terminal-root-install.json'
old=json.loads(reportfile.read_text(encoding='utf-8')) if reportfile.exists() else {}
hashes={}
for index,(dest,(source,mode)) in enumerate(files.items()):
    digest=hashlib.sha256(source.read_bytes()).hexdigest();hashes[dest]=digest
    exists=native('/system/bin/sh','-c','test -e '+shlex.quote(dest)+' && echo yes || true')=='yes'
    if exists:assert native('/system/bin/sha256sum',dest).split()[0] in {digest,old.get('hashes',{}).get(dest)},'Unexpected existing helper; retained: '+dest
    subprocess.run(adb+['push',str(source),'/tmp/leo-terminal-install-'+str(index)],check=True,capture_output=True,timeout=25)
    assert native('/system/bin/sha256sum','/tmp/leo-terminal-install-'+str(index)).split()[0]==digest
# Stop only a verified standalone broker when replacing its executable. Init's
# service is stopped through ctl.stop instead, preventing an automatic restart.
running=native('/system/bin/sh','-c','pidof leo-terminal-root || true')
if running:
    native('/system/bin/setprop','ctl.stop','leo_terminal_root')
    for pid in running.split():
        assert pid.isdigit()
        try:
            argv=native('/system/bin/cat','/proc/'+pid+'/cmdline').split('\x00')
            status=native('/system/bin/cat','/proc/'+pid+'/status')
        except subprocess.CalledProcessError:
            continue
        assert argv[0]=='/system/bin/leo-terminal-root' and any(x in argv for x in ('--daemon','--serve'))
        assert re.search(r'^Uid:\s+0\s+0\s+0\s+0\s*$',status,re.M)
        native('/system/bin/kill','-TERM',pid)
native('/system/bin/mount','-o','remount,rw','/system')
try:
    for index,(dest,(source,mode)) in enumerate(files.items()):
        native('/system/bin/cp','/tmp/leo-terminal-install-'+str(index),dest+'.new');native('/system/bin/chown','0:0',dest+'.new');native('/system/bin/chmod',format(mode,'o'),dest+'.new');native('/system/bin/restorecon',dest+'.new');native('/system/bin/mv',dest+'.new',dest);native('/system/bin/restorecon',dest)
    native('/system/bin/sync')
finally:native('/system/bin/mount','-o','remount,ro','/system')
for dest,digest in hashes.items():assert native('/system/bin/sha256sum',dest).split()[0]==digest
# No parser reload or Android restart. This independent process uses the same
# foreground server mode under init on later ordinary boots.
running=native('/system/bin/sh','-c','pidof leo-terminal-root || true')
if not running:subprocess.run(['python','-X','utf8',str(root/'framework-command.py'),'/system/bin/leo-terminal-root','--daemon'],check=True,timeout=25)
report={'allowed_uid':uid,'hashes':hashes,'stock_su_modified':False,'setuid_file_installed':False,'boot_recovery_written':False}
reportfile.write_text(json.dumps(report,indent=2),encoding='utf-8')
print('Leo-only root broker installed; stock su and Android framework retained.')
