"""Install the selected-SIM IMS runtime; boot/recovery images are untouched."""
from pathlib import Path
import argparse, hashlib, importlib.util, json, re, subprocess, sys

root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('radio',root/'install-radio-runtime.py')
radio=importlib.util.module_from_spec(spec);spec.loader.exec_module(radio)
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--install',action='store_true')
args=parser.parse_args()
files={
    '/system/vendor/leo-ims/bin/imsqmidaemon':(root.parent/'analysis/system/bin/imsqmidaemon',0o755),
    '/system/vendor/leo-ims/bin/imsdatadaemon':(root.parent/'analysis/system/bin/imsdatadaemon',0o755),
    '/system/vendor/leo-ims/sockets.sh':(root/'ims-runtime/sockets.sh',0o755),
    '/system/vendor/leo-ims/restore.sh':(root/'ims-runtime/restore.sh',0o755),
    '/system/vendor/leo-ims/watchdog.sh':(root/'ims-runtime/watchdog.sh',0o755),
    '/system/etc/init/leo-ims.rc':(root/'ims-runtime/init.leo.ims.rc',0o644),
    '/system/priv-app/LeoIms/LeoIms.apk':(root/'output/ims-reference/ported-ims.apk',0o644),
}
expected={target:hashlib.sha256(src.read_bytes()).hexdigest() for target,(src,_) in files.items()}
print('Prepared seven IMS runtime files.',flush=True)
if not args.install:raise SystemExit(0)
assert 'androidboot.serialno=695d' in radio.shell('/system/bin/cat','/proc/cmdline')
assert radio.shell('/system/bin/cat','/leo-framework-internal')=='Validated internal Android partitions.'
assert re.findall(r'^\s*mCallState=(\d+)',radio.shell('/system/bin/dumpsys','telephony.registry'),re.M)==['0','0'],'Finish calls before installation.'
assert radio.shell('/system/bin/getprop','sys.leo.ims.native')!='1','Disable the active runtime and wait for teardown before updating.'
assert not radio.shell('/system/bin/sh','-c','pidof org.codeaurora.ims:leo_ims imsqmidaemon imsdatadaemon || true'),'A previous IMS trial is still active.'
backup=root/'output/ims-runtime-backup';backup.mkdir(parents=True,exist_ok=True)
if not (backup/'properties.json').exists():
    properties={key:radio.shell('/system/bin/getprop',key) for key in ('persist.leo.ims.enabled','persist.dbg.volte_avail_ovr')}
    (backup/'properties.json').write_text(json.dumps(properties,indent=2),encoding='utf-8')
if not (backup/'original-package.apk').exists():
    package=subprocess.check_output([sys.executable,str(root/'framework-command.py'),'/system/bin/sh','/system/bin/pm','path','org.codeaurora.ims'],timeout=30).decode().strip()
    assert package.startswith('package:/') and '\n' not in package
    radio.shell('/system/bin/cp',package[len('package:'):],'/tmp/leo-ims-original.apk')
    subprocess.run(radio.ADB+['pull','/tmp/leo-ims-original.apk',str(backup/'original-package.apk')],check=True)
for index,(target,(src,mode)) in enumerate(files.items()):
    original=backup/(str(index)+'-'+src.name)
    if not original.exists() and radio.shell('/system/bin/sh','-c','test -f '+target+'; echo $?')=='0':
        radio.shell('/system/bin/cp',target,'/tmp/leo-ims-backup-'+str(index))
        subprocess.run(radio.ADB+['pull','/tmp/leo-ims-backup-'+str(index),str(original)],check=True)
    subprocess.run(radio.ADB+['push',str(src),'/tmp/leo-ims-install-'+str(index)],check=True)
    assert radio.shell('/system/bin/sha256sum','/tmp/leo-ims-install-'+str(index)).split()[0]==expected[target]
radio.shell('/system/bin/mount','-o','remount,rw','/system')
try:
    for index,(target,(src,mode)) in enumerate(files.items()):
        radio.shell('/system/bin/mkdir','-p',target.rsplit('/',1)[0])
        radio.shell('/system/bin/cp','/tmp/leo-ims-install-'+str(index),target+'.new')
        radio.shell('/system/bin/chmod',format(mode,'o'),target+'.new')
        radio.shell('/system/bin/chown','0:0',target+'.new')
        radio.shell('/system/bin/restorecon',target+'.new')
        radio.shell('/system/bin/mv',target+'.new',target)
finally:radio.shell('/system/bin/mount','-o','remount,ro','/system')
for target,digest in expected.items():
    assert radio.shell('/system/bin/sha256sum',target).split()[0]==digest
subprocess.run([sys.executable,str(root/'framework-command.py'),'/system/bin/sh','/system/bin/pm','install','-r','/tmp/leo-ims-install-6'],check=True,timeout=60)
radio.shell('/system/bin/setprop','persist.leo.ims.enabled','1')
(root/'output/ims-runtime-installed.json').write_text(json.dumps(expected,indent=2),encoding='utf-8')
print('Installed and verified. The new native init services require a reboot.',flush=True)
