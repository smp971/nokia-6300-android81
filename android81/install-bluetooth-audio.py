"""Install the AOSP A2DP PCM module and Leo legacy route policy; retain verified backups."""
from pathlib import Path
import argparse,hashlib,json,re,shlex,subprocess
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--restore',action='store_true');args=parser.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def run(*a):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*a])],timeout=25).decode(errors='replace').strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def exists(p):return run('/system/bin/sh','-c','[ -f '+shlex.quote(p)+' ] && echo yes || true')=='yes'
assert 'androidboot.serialno=695d' in run('/system/bin/cat','/proc/cmdline')
calls=re.findall(r'mCallState=(\d+)',run('/system/bin/dumpsys','telephony.registry'));assert calls and set(calls)=={'0'},'Refusing audio restart during a call'
assert 'ServiceRecord' not in run('/system/bin/dumpsys','activity','services','ro.leo.visual/.RecordingService'),'Stop and save the recorder first'
files={'/system/lib/hw/audio.a2dp.default.so':root/'output/audio.a2dp.default.so','/system/etc/audio_policy.conf':root/'device/nokia/leo/audio_policy.conf'}
backup=root/'output/bt-audio-before';backup.mkdir(exist_ok=True);manifest=backup/'manifest.json';records=json.loads(manifest.read_text()) if manifest.exists() else {}
for i,(target,source) in enumerate(files.items()):
 current=run('/system/bin/sha256sum',target).split()[0] if exists(target) else None
 if target not in records:
  assert not args.restore,'No original recorded'
  saved=backup/(str(i)+'-'+Path(target).name)
  if current:
   run('/system/bin/cp',target,'/tmp/leo-bt-original');subprocess.run(adb+['pull','/tmp/leo-bt-original',str(saved)],check=True,capture_output=True);assert digest(saved)==current
  records[target]={'before':current,'saved':str(saved),'after':None}
 record=records[target];assert current in (record['before'],record['after']),'Unexpected local change: '+target
 if args.restore and not record['before']:continue
 chosen=Path(record['saved']) if args.restore else source
 assert chosen.is_file();expected=digest(chosen)
 if not args.restore:record['after']=expected
 subprocess.run(adb+['push',str(chosen),'/tmp/leo-bt-'+str(i)],check=True,capture_output=True);assert run('/system/bin/sha256sum','/tmp/leo-bt-'+str(i)).split()[0]==expected
manifest.write_text(json.dumps(records,indent=2))
run('/system/bin/setprop','ctl.stop','audioserver')
try:
 run('/system/bin/mount','-o','remount,rw','/system')
 try:
  for i,target in enumerate(files):
   record=records[target]
   if args.restore and not record['before']:run('/system/bin/rm','-f',target);continue
   run('/system/bin/cp','/tmp/leo-bt-'+str(i),target+'.leo-new');run('/system/bin/chmod','644',target+'.leo-new');run('/system/bin/chown','0:0',target+'.leo-new');run('/system/bin/mv',target+'.leo-new',target);run('/system/bin/restorecon',target)
   assert run('/system/bin/sha256sum',target).split()[0]==record['before' if args.restore else 'after']
 finally:run('/system/bin/sync');run('/system/bin/mount','-o','remount,ro','/system')
finally:run('/system/bin/setprop','ctl.start','audioserver')
print('Original audio module/policy restored' if args.restore else 'A2DP module and policy installed; audioserver restarted; system read-only; kernel and Bluetooth unchanged')
