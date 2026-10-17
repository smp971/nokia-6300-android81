"""Install only stock mixer/calibration data, without changing Android's active audio HAL."""
from pathlib import Path
import argparse,hashlib,json,subprocess,shlex,tarfile
p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');args=p.parse_args()
here=Path(__file__).resolve().parent;stock=here.parent/'analysis/system/etc';out=here/'output'
files=[stock/'mixer_paths.xml',stock/'audio_platform_info.xml']+sorted((stock/'acdbdata').rglob('*'))
files=[p for p in files if p.is_file()];hashes={str(p.relative_to(stock)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
archive=out/'audio-stock-config.tar'
with tarfile.open(archive,'w') as t:
 for p in files:
  info=t.gettarinfo(str(p),arcname=str(p.relative_to(stock)).replace('\\','/'));info.mode=0o644;info.uid=info.gid=0
  with p.open('rb') as f:t.addfile(info,f)
print('Prepared',len(files),'mixer/calibration files.',flush=True)
if not args.install:raise SystemExit(0)
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def call(*a):
 r=subprocess.run(adb+list(a),capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=30)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip()
def shell(s):return call('shell','/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(s))
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('cat /leo-framework-internal')=='Validated internal Android partitions.'
for name,h in hashes.items():
 existing=shell('if test -e /system/etc/'+name+'; then sha256sum /system/etc/'+name+'; fi')
 assert not existing or existing.split()[0]==h,'Refusing to replace different existing '+name
call('push',str(archive),'/tmp/audio-stock-config.tar');shell('mkdir -p /tmp/audio-stock-config; tar -xf /tmp/audio-stock-config.tar -C /tmp/audio-stock-config')
shell("set -e; mount -o remount,rw /system; trap 'mount -o remount,ro /system' EXIT; cp -R /tmp/audio-stock-config/* /system/etc/; restorecon -R /system/etc/acdbdata /system/etc/mixer_paths.xml /system/etc/audio_platform_info.xml; sync")
for name,h in hashes.items():assert shell('sha256sum /system/etc/'+name).split()[0]==h
assert ' /system ext4 ro,' in shell('cat /proc/mounts')
(out/'audio-stock-config-installed.json').write_text(json.dumps(hashes,indent=2))
print('Verified',len(hashes),'stock files; system read-only; active HAL unchanged.')
