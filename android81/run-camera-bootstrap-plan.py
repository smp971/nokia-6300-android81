"""Isolated stock sensor bootstrap; default plan refuses sensor ioctls.

--probe-sensors permits only CFG_SINIT_PROBE, changing volatile kernel sensor state.
--enumerate-ready sends DONE only for an already registered sensor. WAIT is never
forwarded to the kernel. No camera open, preview or capture. Private dependencies must
already exist in the marked camera diagnostic directory; no global installation.
"""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess,tarfile
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);modes=p.add_mutually_exclusive_group();modes.add_argument('--probe-sensors',action='store_true');modes.add_argument('--enumerate-ready',action='store_true');args=p.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d'];folder='/data/local/tmp/leo-camera-probe'
def native(*cmd):
 return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*cmd])],timeout=25).decode(errors='replace').strip()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','sys.boot_completed')=='1'
assert native('/system/bin/cat',folder+'/.owner')=='Leo camera linker sandbox v1'
assert not native('/system/bin/sh','-c','pidof bootstrap-plan probe-enum mm-qcamera-daemon || true'),'Earlier probe or daemon still running'
calls=native('/system/bin/dumpsys','telephony.registry');assert 'mCallState=1' not in calls and 'mCallState=2' not in calls
# The shared dependencies come from the earlier linker sandbox. Stage only the
# five original sensor plugins in the same marked private directory.
names=['libmmcamera_'+sensor+'.so' for sensor in ['gc2385','gc2395','bf20a1','bf2253L','gc030a']]
archive=root/'output/camera-bootstrap-sensors.tar'
with tarfile.open(archive,'w') as tar:
 for name in names:
  source=root.parent/'analysis/system/vendor/lib'/name
  assert source.is_file() and not source.is_symlink()
  info=tar.gettarinfo(str(source),arcname=name);info.mode=0o644;info.uid=info.gid=0
  with source.open('rb') as stream:tar.addfile(info,stream)
subprocess.run(adb+['push',str(archive),'/tmp/camera-bootstrap-sensors.tar'],check=True,timeout=25)
assert native('/system/bin/sha256sum','/tmp/camera-bootstrap-sensors.tar').split()[0]==hashlib.sha256(archive.read_bytes()).hexdigest()
native('/system/bin/tar','-xf','/tmp/camera-bootstrap-sensors.tar','-C',folder)
for name in names+['libmmcamera2_sensor_modules.so','liboemcamera.so','libmmcamera_interface.so','camera.msm8909.so']:
 source=next(path for path in [root.parent/'analysis/system/vendor/lib'/name,root.parent/'analysis/system/lib'/name,root.parent/'analysis/system/lib/hw'/name] if path.is_file())
 assert native('/system/bin/sha256sum',folder+'/'+name).split()[0]==hashlib.sha256(source.read_bytes()).hexdigest(),name
binary=root/'output/leo-camera-bootstrap-plan'
subprocess.run(adb+['push',str(binary),'/tmp/leo-camera-bootstrap-plan'],check=True,timeout=25)
assert native('/system/bin/sha256sum','/tmp/leo-camera-bootstrap-plan').split()[0]==hashlib.sha256(binary.read_bytes()).hexdigest()
native('/system/bin/cp','/tmp/leo-camera-bootstrap-plan',folder+'/bootstrap-plan');native('/system/bin/chmod','755',folder+'/bootstrap-plan')
def state():
 return dict(boot=native('/system/bin/cat','/proc/sys/kernel/random/boot_id'),audio=native('/system/bin/getprop','sys.leo.audio.state'),surfaceflinger=native('/system/bin/pidof','surfaceflinger'),system_server=native('/system/bin/pidof','system_server'),nodes=native('/system/bin/sh','-c','for n in /sys/class/video4linux/*; do echo "$n"; cat "$n/name"; done'))
before=state()
if args.enumerate_ready:
 assert 'camera 3-007c' in before['nodes'],'Registered sensor required before DONE'
 assert native('/system/bin/cat','/sys/class/video4linux/v4l-subdev5/name')=='msm_sensor_init'
mode='--probe-sensors' if args.probe_sensors else '--enumerate-ready' if args.enumerate_ready else '--plan';log=folder+'/bootstrap.log'
command=['/system/bin/env','LD_LIBRARY_PATH='+folder+':/vendor/lib:/system/lib',folder+'/bootstrap-plan',mode]
try:
 result=subprocess.run(adb+['shell',shlex.join(['/sbin/leo-framework-exec','/system/bin/sh','-c',shlex.join(command)+' > '+log+' 2>&1'])],capture_output=True,timeout=22)
 status=result.returncode
except subprocess.TimeoutExpired:status=124
report=dict(mode=mode,returncode=status,stdout=native('/system/bin/cat',log),before=before,after=state(),remaining=native('/system/bin/sh','-c','pidof bootstrap-plan || true'))
(root/'output'/('camera-bootstrap-'+mode[2:]+'.json')).write_text(json.dumps(report,indent=2),encoding='utf8')
print(report['stdout']);print('Core state unchanged:',all(before[k]==report['after'][k] for k in ['boot','audio','surfaceflinger','system_server']));print('Remaining:',report['remaining']);print('Video nodes:\n'+report['after']['nodes'])
raise SystemExit(status)
