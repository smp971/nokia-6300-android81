"""Install or roll back Leo's framebuffer sync configuration; never writes boot/recovery."""
import argparse,hashlib,json,shlex,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--rollback',action='store_true');p.add_argument('--update',action='store_true');args=p.parse_args()
assert not (args.rollback and args.update)
here=Path(__file__).resolve().parent;adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def call(*a):
 r=subprocess.run(adb+list(a),capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip()
def shell(s):return call('shell','/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(s))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('cat /leo-framework-internal')=='Validated internal Android partitions.'
backup=here/'output/ui-sync-backup';backup.mkdir(exist_ok=True)
targets=[('hwcomposer-default','/system/vendor/lib/hw/hwcomposer.default.so','hwcomposer-ui.so',644),('hwcomposer-leo','/system/vendor/lib/hw/hwcomposer.leo.so','hwcomposer-ui.so',644),('hwcomposer-staged','/system/vendor/lib/leo-adreno/hwcomposer-adreno.so','hwcomposer-ui.so',644),('configstore','/system/vendor/bin/hw/android.hardware.configstore@1.0-service','configstore-ui',755),('surfaceflinger','/system/lib/libsurfaceflinger.so','libsurfaceflinger-ui.so',644)]
manifest=backup/'manifest.json'
if args.update:
 records=json.loads(manifest.read_text())
 for label,target,new,mode in targets:
  record=next((r for r in records if r['label']==label),None)
  oldhash=shell('sha256sum '+target).split()[0]
  if record is None:
   shell('cp '+target+' /tmp/ui-sync-'+label)
   call('pull','/tmp/ui-sync-'+label,str(backup/label));assert sha(backup/label)==oldhash
   record={'label':label,'target':target,'old_sha256':oldhash}
   records.append(record)
  else:assert oldhash==record['new_sha256'],target
  record['new_sha256']=sha(here/'output'/new)
 manifest.write_text(json.dumps(records,indent=2))
elif not args.rollback:
 assert not manifest.exists(),'A backup already exists: inspect it before another install.'
 records=[]
 for label,target,new,mode in targets:
  oldhash=shell('sha256sum '+target).split()[0]
  shell('cp '+target+' /tmp/ui-sync-'+label)
  call('pull','/tmp/ui-sync-'+label,str(backup/label));assert sha(backup/label)==oldhash
  records.append({'label':label,'target':target,'old_sha256':oldhash,'new_sha256':sha(here/'output'/new)})
 manifest.write_text(json.dumps(records,indent=2))
else:
 records=json.loads(manifest.read_text())
for label,target,new,mode in targets:
 source=backup/label if args.rollback else here/'output'/new
 record=next(r for r in records if r['label']==label)
 assert sha(source)==record['old_sha256' if args.rollback else 'new_sha256']
 call('push',str(source),'/tmp/ui-new-'+label)
 assert shell('sha256sum /tmp/ui-new-'+label).split()[0]==sha(source)
for service in ['zygote','surfaceflinger','gralloc-2-0','hwcomposer-2-1','configstore-hal-1-0']:
 shell('setprop ctl.stop '+service)
time.sleep(3)
try:
 commands=['set -e','mount -o remount,rw /system',"trap 'mount -o remount,ro /system' EXIT"]
 for label,target,new,mode in targets:
  commands.extend(['cp /tmp/ui-new-'+label+' '+target+'.new','chmod '+str(mode)+' '+target+'.new','chown 0:0 '+target+'.new','restorecon '+target+'.new','mv -f '+target+'.new '+target,'restorecon '+target])
 commands.append('sync');shell('; '.join(commands))
 for r in records:assert shell('sha256sum '+r['target']).split()[0]==r['old_sha256' if args.rollback else 'new_sha256']
finally:
 shell('setprop sys.boot_completed 0')
 for service in ['configstore-hal-1-0','hwcomposer-2-1','gralloc-2-0','surfaceflinger','zygote']:
  shell('setprop ctl.start '+service)
print('Restored previous graphics configuration.' if args.rollback else 'Installed UI sync configuration; backups verified. Framework is restarting.')
