"""Install the Leo audio-backed vibrator HAL and stock boot properties; retain local rollback."""
from pathlib import Path
import argparse,subprocess,hashlib,json,re
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--activate',action='store_true');p.add_argument('--undo',action='store_true');args=p.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def run(*a):return subprocess.check_output(['python','-X','utf8',str(root/'framework-command.py'),*a],timeout=40).decode(errors='replace').strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in run('/system/bin/cat','/proc/cmdline')
states=re.findall(r'mCallState=(\d+)',run('/system/bin/dumpsys','telephony.registry'));assert states and set(states)=={'0'},'Finish calls first'
folder=root/'output/audio-vibrator-before';folder.mkdir(exist_ok=True);index=folder/'manifest.json'
files={'/system/vendor/lib/hw/android.hardware.vibrator@1.0-impl.so':root/'output/android.hardware.vibrator@1.0-impl.so','/system/etc/init/leo-haptic.rc':root/'device/nokia/leo/init.leo.haptic.rc'}
properties={'vendor.audio.haptic_audio':'true','vendor.audio.haptic_freq':'157','vendor.audio.haptic_ampl':'100'}
if not index.exists():
 assert not args.undo
 record={'files':{},'properties':{k:run('/system/bin/getprop',k) for k in properties}}
 for n,destination in enumerate(files):
  exists=run('/system/bin/sh','-c','test -f '+destination+' && echo yes || true')=='yes'
  if exists:
   saved=folder/str(n);run('/system/bin/cp',destination,'/tmp/leo-haptic-before-'+str(n));subprocess.run(adb+['pull','/tmp/leo-haptic-before-'+str(n),str(saved)],check=True,capture_output=True)
   record['files'][destination]={'path':str(saved),'sha256':digest(saved)}
  else:record['files'][destination]=None
 index.write_text(json.dumps(record,indent=2))
record=json.loads(index.read_text());staged={}
for n,(destination,source) in enumerate(files.items()):
 if args.undo:
  before=record['files'][destination]
  if before is None:staged[destination]=None;continue
  source=Path(before['path']);assert digest(source)==before['sha256']
 exists=run('/system/bin/sh','-c','test -f '+destination+' && echo yes || true')=='yes'
 if exists and run('/system/bin/sha256sum',destination).split()[0]==digest(source):continue
 if destination.endswith('.so') and not args.activate:
  for pid in run('/system/bin/pidof','system_server').split():
   assert destination not in run('/system/bin/cat','/proc/'+pid+'/maps'),'Use --activate when replacing a vibrator HAL already mapped by system_server'
 temporary='/tmp/leo-haptic-install-'+str(n);subprocess.run(adb+['push',str(source),temporary],check=True,capture_output=True);assert run('/system/bin/sha256sum',temporary).split()[0]==digest(source);staged[destination]=temporary
for key,value in (record['properties'] if args.undo else properties).items():run('/system/bin/setprop',key,value)
run('/system/bin/mount','-o','remount,rw','/system')
try:
 for destination,temporary in staged.items():
  if temporary is None:run('/system/bin/rm','-f',destination);continue
  run('/system/bin/cp',temporary,destination+'.new');run('/system/bin/chmod','644',destination+'.new');run('/system/bin/chown','0:0',destination+'.new');run('/system/bin/mv',destination+'.new',destination);run('/system/bin/restorecon',destination)
 run('/system/bin/sync')
 if args.activate:
  run('/system/bin/setprop','ctl.restart','audioserver')
  run('/system/bin/sleep','3')
  assert run('/system/bin/getprop','init.svc.audioserver')=='running'
  run('/system/bin/setprop','ctl.restart','zygote')
  run('/system/bin/sleep','2')
finally:
 for attempt in range(5):
  try:run('/system/bin/mount','-o','remount,ro','/system');break
  except subprocess.CalledProcessError:
   if attempt==4:raise
   run('/system/bin/sleep','1')
print('Audio vibrator restored' if args.undo else 'Audio vibrator HAL and boot properties installed')
if args.activate:print('Android framework restarted to load the vibrator HAL')
