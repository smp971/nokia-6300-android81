"""Bounded, private-namespace camera enumeration; never opens a camera.

Requires dependencies already staged by run-camera-linker-probe.py.
Only --devices grants camera nodes inside the child namespace. Original device
permissions, services and system libraries remain untouched.
"""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--devices',action='store_true')
args=parser.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*cmd):
    return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*cmd])],timeout=25).decode(errors='replace').strip()
assert 'androidboot.serialno=695d ' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','sys.boot_completed')=='1'
pending=native('/system/bin/sh','-c','pidof probe-enum || true')
if pending:raise SystemExit('An earlier camera probe is still present. Inspect its kernel wait; do not launch another hardware probe before recovery.')
calls=native('/system/bin/dumpsys','telephony.registry')
assert 'mCallState=1' not in calls and 'mCallState=2' not in calls
folder='/data/local/tmp/leo-camera-probe'
assert native('/system/bin/cat',folder+'/.owner')=='Leo camera linker sandbox v1'
binary=root/'output/leo-camera-enumeration-probe'
sha=hashlib.sha256(binary.read_bytes()).hexdigest()
subprocess.run(adb+['push',str(binary),'/tmp/leo-camera-enumeration-probe'],check=True,timeout=25)
assert native('/system/bin/sha256sum','/tmp/leo-camera-enumeration-probe').split()[0]==sha
native('/system/bin/cp','/tmp/leo-camera-enumeration-probe',folder+'/probe-enum')
native('/system/bin/chmod','755',folder+'/probe-enum')
def state():
    return dict(boot=native('/system/bin/cat','/proc/sys/kernel/random/boot_id'),audio=native('/system/bin/getprop','sys.leo.audio.state'),
                surfaceflinger=native('/system/bin/pidof','surfaceflinger'),system_server=native('/system/bin/pidof','system_server'))
before=state()
command=['/sbin/leo-framework-exec','/system/bin/env','LD_LIBRARY_PATH='+folder+':/vendor/lib:/system/lib',folder+'/probe-enum']
if args.devices:command.append('--devices')
log=folder+'/enumeration.log'
wrapped=['/sbin/leo-framework-exec','/system/bin/sh','-c',shlex.join(command[1:])+' > '+log+' 2>&1']
try:
    probe=subprocess.run(adb+['shell',shlex.join(wrapped)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=20)
    returncode=probe.returncode;stderr=probe.stderr
except subprocess.TimeoutExpired:
    returncode=124;stderr='Host timeout; child has independent 12-second alarm. Inspect device log/state.'
report=dict(mode='camera-nodes' if args.devices else 'hardware-hidden',binary_sha256=sha,returncode=returncode,
            stdout=native('/system/bin/cat',log),stderr=stderr,before=before,after=state(),camera_open_called=False)
(root/'output'/('camera-enumeration-'+report['mode']+'.json')).write_text(json.dumps(report,indent=2))
print(report['stdout']);print(report['stderr']);print('System state unchanged:',report['before']==report['after'])
raise SystemExit(returncode)
