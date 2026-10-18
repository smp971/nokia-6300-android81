"""Stage/install the stock audio bridge with verified backups; no boot/recovery writes."""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess,tarfile
p=argparse.ArgumentParser();p.add_argument('--install',action='store_true');p.add_argument('--activate',action='store_true');p.add_argument('--rollback',action='store_true');args=p.parse_args()
here=Path(__file__).resolve().parent;out=here/'output';stock=here.parent/'analysis/system';adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def call(*a):
 r=subprocess.run(adb+list(a),capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=60)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip()
def shell(s):return call('shell','/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(s))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('cat /leo-framework-internal')=='Validated internal Android partitions.'
base='vendor/leo-audio/';target='/system/vendor/lib/hw/audio.primary.default.so'
if args.rollback:
 assert shell('sha256sum /vendor/leo-audio/lib/audio-primary-generic.so').split()[0]=='9cab0a997b57918d99c3ac7d074e2b4bc201ae0590b7b972ec5322d94ab0cad6'
 shell('setprop persist.leo.audio.autoload 0; setprop persist.leo.audio.enabled 0; setprop persist.leo.audio.validated 0; setprop ctl.stop audioserver; setprop ctl.stop audio-hal-2-0; sleep 1')
 shell("set -e; mount -o remount,rw /system; trap 'mount -o remount,ro /system' EXIT; cp /vendor/leo-audio/lib/audio-primary-generic.so "+target+".new; chmod 644 "+target+".new; mv -f "+target+".new "+target+"; restorecon "+target+"; sync")
 shell('setprop ctl.start audio-hal-2-0; setprop ctl.start audioserver; setprop sys.leo.audio.state disabled')
 print('Original generic HAL restored; private files remain inactive.');raise SystemExit(0)
assert shell('uname -r')=='4.9.240-perf-g4f9e270fd77f'
assert shell('getprop ro.boot.hwid')=='0x0'
files={}
for name in ['libtinycompress.so','libaudioroute.so','libacdbloader.so','libaudcal.so','libacdbrtac.so','libadiertac.so','libacdb-fts.so','audio.primary.msm8909.so']:
 choices=list(stock.glob('**/'+name));assert len(choices)==1
 files[base+'lib/'+name]=(choices[0],0o644)
for name in ['libmdmdetect.so','libperipheral_client.so','libdiag.so']:files[base+'lib/'+name]=(out/'radio-probe/lib'/name,0o644)
for module in (stock/'lib/modules').glob('audio*.ko'):files[base+'modules/'+module.name]=(module,0o644)
assert len([x for x in files if x.endswith('.ko')])==12
files[base+'start.sh']=(here/'audio-runtime/start.sh',0o755)
files['etc/init/init.leo.audio.rc']=(here/'audio-runtime/init.leo.audio.rc',0o644)
files['vendor/lib/hw/audio.primary.default.so']=(out/'audio-primary-bridge.so',0o644)
assert (out/'audio-primary-bridge.so').is_file()
old=shell('sha256sum '+target).split()[0]
assert old=='9cab0a997b57918d99c3ac7d074e2b4bc201ae0590b7b972ec5322d94ab0cad6','Refusing to overwrite a different HAL or existing backup.'
if not args.install:
 print('Ready to install',len(files)+1,'files. Use --install; activation is separate with --activate.');raise SystemExit(0)
assert not shell('test -e /vendor/leo-audio && echo exists || true')
shell('cp '+target+' /tmp/leo-audio-generic.so');backup=out/'audio-primary-generic-before-integration.so';call('pull','/tmp/leo-audio-generic.so',str(backup));assert sha(backup)==old
files[base+'lib/audio-primary-generic.so']=(backup,0o644)
expected={name:sha(source) for name,(source,_) in files.items()}
bundle=out/'audio-runtime-install.tar'
with tarfile.open(bundle,'w') as t:
 for name,(source,mode) in files.items():
  info=t.gettarinfo(str(source),arcname=name);info.uid=info.gid=0;info.mode=mode
  with source.open('rb') as f:t.addfile(info,f)
call('push',str(bundle),'/tmp/audio-runtime-install.tar')
assert shell('sha256sum /tmp/audio-runtime-install.tar').split()[0]==sha(bundle)
shell('mkdir -p /tmp/audio-runtime-install; tar -xf /tmp/audio-runtime-install.tar -C /tmp/audio-runtime-install')
shell('setprop persist.leo.audio.enabled 0; setprop ctl.stop audioserver; setprop ctl.stop audio-hal-2-0; sleep 1')
try:
 shell("set -e; mount -o remount,rw /system; trap 'mount -o remount,ro /system' EXIT; cp -R /tmp/audio-runtime-install/vendor/leo-audio /system/vendor/; cp /tmp/audio-runtime-install/etc/init/init.leo.audio.rc /system/etc/init/; cp /tmp/audio-runtime-install/vendor/lib/hw/audio.primary.default.so "+target+".new; chmod 644 "+target+".new; mv -f "+target+".new "+target+"; restorecon -R /system/vendor/leo-audio /system/etc/init/init.leo.audio.rc; restorecon "+target+"; sync")
finally:
 shell('setprop ctl.start audio-hal-2-0; setprop ctl.start audioserver')

for name,h in expected.items():assert shell('sha256sum /system/'+name).split()[0]==h,name
assert ' /system ext4 ro,' in shell('cat /proc/mounts')
(out/'audio-runtime-installed.json').write_text(json.dumps(expected,indent=2))
print('Installed and verified',len(expected),'files; system read-only.',flush=True)
if args.activate:
 shell('setprop persist.leo.audio.validated 0; setprop persist.leo.audio.autoload 1')
 print(shell('/system/bin/sh /vendor/leo-audio/start.sh setup'),flush=True)
 print('Audio state:',shell('getprop sys.leo.audio.state'),flush=True)
