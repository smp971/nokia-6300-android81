"""Install only the tested netmgr extension of the existing private radio runtime."""
from pathlib import Path
import argparse, hashlib, importlib.util, json, subprocess

root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('radio_installer',root/'install-radio-runtime.py')
radio=importlib.util.module_from_spec(spec);spec.loader.exec_module(radio)
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--install',action='store_true');args=p.parse_args()
stock=root.parent/'analysis/system'
files={
    '/system/vendor/leo-radio/bin/netmgrd':(stock/'bin/netmgrd',0o755),
    '/system/etc/data/dsi_config.xml':(stock/'etc/data/dsi_config.xml',0o644),
    '/system/etc/data/netmgr_config.xml':(stock/'etc/data/netmgr_config.xml',0o644),
    '/system/vendor/leo-radio/start.sh':(root/'radio-runtime/start.sh',0o755),
    '/system/etc/init/leo-radio.rc':(root/'radio-runtime/init.leo.radio.rc',0o644),
}
expected={name:hashlib.sha256(src.read_bytes()).hexdigest() for name,(src,mode) in files.items()}
print('Prepared five data runtime files.',flush=True)
if not args.install:raise SystemExit(0)
assert 'androidboot.serialno=695d' in radio.shell('/system/bin/cat','/proc/cmdline')
assert radio.shell('/system/bin/cat','/leo-framework-internal')=='Validated internal Android partitions.'
assert radio.shell('/system/bin/getprop','init.svc.leo_rild1')=='running'
backup=root/'output/lte-investigation/data-runtime-backup';backup.mkdir(parents=True,exist_ok=True)
for index,(target,(src,mode)) in enumerate(files.items()):
    if not (backup/src.name).exists() and radio.shell('/system/bin/sh','-c','test -e '+target+'; echo $?')=='0':
        radio.shell('/system/bin/cp',target,'/tmp/leo-data-backup-'+str(index))
        subprocess.run(radio.ADB+['pull','/tmp/leo-data-backup-'+str(index),str(backup/src.name)],check=True)
    subprocess.run(radio.ADB+['push',str(src),'/tmp/leo-data-file-'+str(index)],check=True)
radio.shell('/system/bin/mount','-o','remount,rw','/system')
try:
    for index,(target,(src,mode)) in enumerate(files.items()):
        radio.shell('/system/bin/mkdir','-p',str(Path(target).parent).replace('\\','/'))
        radio.shell('/system/bin/cp','/tmp/leo-data-file-'+str(index),target+'.new')
        radio.shell('/system/bin/chmod',format(mode,'o'),target+'.new')
        radio.shell('/system/bin/chown','0:0',target+'.new')
        radio.shell('/system/bin/mv',target+'.new',target)
        radio.shell('/system/bin/restorecon',target)
finally:radio.shell('/system/bin/mount','-o','remount,ro','/system')
for target,digest in expected.items():assert radio.shell('/system/bin/sha256sum',target).split()[0]==digest
radio.shell('/system/bin/setprop','persist.leo.data.enabled','1')
(root/'output/lte-investigation/data-runtime-installed.json').write_text(json.dumps(expected,indent=2))
print('Installed and verified; new init service requires reboot.',flush=True)
