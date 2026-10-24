"""Install the isolated GNSS service, original GPS config and feature declaration."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,re,subprocess
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('r',root/'install-radio-runtime.py');r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--install',action='store_true');args=p.parse_args()
product=Path('//wsl.localhost/kaios/root/android81-leo/src/out/target/product/leo/system')
stock=root.parent/'analysis/system'
files={
 '/system/vendor/leo-gps/exec':(root/'output/leo-gps-runtime-exec',0o755),
 '/system/vendor/leo-gps/gnss-provider':(product/'vendor/bin/hw/android.hardware.gnss@1.0-service',0o755),
 '/system/vendor/leo-gps/hw/android.hardware.gnss@1.0-impl.so':(product/'vendor/lib/hw/android.hardware.gnss@1.0-impl.so',0o644),
 '/system/vendor/leo-gps/hw/gps.default.so':(stock/'lib/hw/gps.default.so',0o644),
 '/system/etc/gps.conf':(stock/'etc/gps.conf',0o644),
 '/system/etc/permissions/android.hardware.location.gps.xml':(stock/'etc/permissions/android.hardware.location.gps.xml',0o644),
 '/system/etc/init/leo-gps.rc':(root/'init.leo.gps.rc',0o644),
}
for name in ['libloc_eng.so','libloc_core.so','libloc_api_v02.so','libloc_ds_api.so','libgps.utils.so']:
 files['/system/vendor/leo-gps/lib/'+name]=(stock/'lib'/name,0o644)
hashes={name:hashlib.sha256(src.read_bytes()).hexdigest() for name,(src,mode) in files.items()}
print('Prepared',len(files),'GNSS files; global HAL libraries are unchanged.')
if not args.install:raise SystemExit(0)
assert 'androidboot.serialno=695d' in r.shell('/system/bin/cat','/proc/cmdline')
assert r.shell('/system/bin/cat','/leo-framework-internal')=='Validated internal Android partitions.'
assert re.findall(r'^\s*mCallState=(\d+)',r.shell('/system/bin/dumpsys','telephony.registry'),re.M)==['0','0']
backup=root/'output/gps-runtime-backup';backup.mkdir(parents=True,exist_ok=True)
for i,(target,(src,mode)) in enumerate(files.items()):
 if not (backup/(str(i)+'-'+src.name)).exists() and r.shell('/system/bin/sh','-c','test -f '+target+'; echo $?')=='0':
  r.shell('/system/bin/cp',target,'/tmp/leo-gps-backup-'+str(i));subprocess.run(r.ADB+['pull','/tmp/leo-gps-backup-'+str(i),str(backup/(str(i)+'-'+src.name))],check=True)
 subprocess.run(r.ADB+['push',str(src),'/tmp/leo-gps-install-'+str(i)],check=True,stdout=subprocess.DEVNULL)
 assert r.shell('/system/bin/sha256sum','/tmp/leo-gps-install-'+str(i)).split()[0]==hashes[target]
r.shell('/system/bin/mount','-o','remount,rw','/system')
try:
 for i,(target,(src,mode)) in enumerate(files.items()):
  r.shell('/system/bin/mkdir','-p',target.rsplit('/',1)[0]);r.shell('/system/bin/cp','/tmp/leo-gps-install-'+str(i),target+'.new')
  r.shell('/system/bin/chmod',format(mode,'o'),target+'.new');r.shell('/system/bin/chown','0:0',target+'.new');r.shell('/system/bin/restorecon',target+'.new');r.shell('/system/bin/mv',target+'.new',target)
finally:r.shell('/system/bin/mount','-o','remount,ro','/system')
for target,digest in hashes.items():assert r.shell('/system/bin/sha256sum',target).split()[0]==digest
r.shell('/system/bin/setprop','persist.leo.gps.enabled','1')
(root/'output/gps-runtime-installed.json').write_text(json.dumps(hashes,indent=2))
print('GNSS runtime installed. Reboot required for early registration and the GPS provider.')
