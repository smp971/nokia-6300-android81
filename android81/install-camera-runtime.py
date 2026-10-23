"""Install the isolated Leo camera runtime. No boot/recovery or platform library replacement.

The stock blobs remain local. Root init services are engineering bring-up only.
Use --disable to stop them and disable future camera startup without deleting files.
"""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess,tarfile
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--install',action='store_true');p.add_argument('--disable',action='store_true');args=p.parse_args()
assert not(args.install and args.disable)
root=Path(__file__).resolve().parent;out=root/'output';stock=root.parent/'analysis/system';adb=['D:/ROMS/platform-tools/adb.exe','-s','695d'];target='/system/vendor/leo-camera'
def native(*cmd):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*cmd])],timeout=50).decode(errors='replace').strip()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/cat','/leo-framework-internal')=='Validated internal Android partitions.'
if args.disable:
 native('/system/bin/setprop','persist.leo.camera.enabled','0');print('Camera boot integration disabled; files and photos preserved.');raise SystemExit(0)
assert native('/system/bin/uname','-r')=='4.9.240-perf-g4f9e270fd77f'
calls=native('/system/bin/dumpsys','telephony.registry');assert 'mCallState=1' not in calls and 'mCallState=2' not in calls
files={'exec':out/'leo-camera-runtime-exec','mm-qcamera-daemon':stock/'bin/mm-qcamera-daemon','hw/camera.qcom.so':out/'camera.qcom.so'}
audit=json.loads((out/'camera-port-audit.json').read_text())
for row in audit['libraries']:
 name=row['name']
 if row['source']!='kaios-private' or not name.endswith('.so'):continue
 files['lib/'+name]=next(p for p in [stock/'lib'/name,stock/'vendor/lib'/name,stock/'lib/hw'/name] if p.is_file())
for path in (stock/'vendor/lib').glob('*.so'):
 if path.name.startswith(('libmmcamera','libchromatix','libactuator')):files['lib/'+path.name]=path
for name in ['libC2D2.so','libc2d30-a3xx.so','libc2d30-a4xx.so','libqomx_jpegenc.so','libqomx_jpegdec.so','libmmqjpeg_codec.so','libmmjpeg.so','libfastcvopt.so','libjpegehw.so','libjpegdhw.so']:
 files['lib/'+name]=stock/'vendor/lib'/name
product=Path('//wsl.localhost/kaios/root/android81-leo/src/out/target/product/leo/system')
files['camera-provider']=product/'vendor/bin/hw/android.hardware.camera.provider@2.4-service'
for name in ['android.hardware.camera.provider@2.4-impl.so','camera.device@1.0-impl.so','camera.device@3.2-impl.so','camera.device@3.3-impl.so']:
 files[('hw/' if name.startswith('android.hardware') else 'lib/')+name]=next(p for p in [product/'vendor/lib/hw'/name,product/'vendor/lib'/name] if p.is_file())
assert all(source.is_file() and not source.is_symlink() for source in files.values())
# Original HAL remains private, outside every global hardware search path.
assert 'lib/camera.msm8909.so' in files
hashes={name:sha(source) for name,source in files.items()};archive=out/'camera-runtime-install.tar'
with tarfile.open(archive,'w') as tar:
 for name,source in files.items():
  info=tar.gettarinfo(str(source),'vendor/leo-camera/'+name);info.uid=info.gid=0;info.mode=0o755 if '/' not in name else 0o644
  with source.open('rb') as stream:tar.addfile(info,stream)
 source=root/'init.leo.camera.rc';info=tar.gettarinfo(str(source),'etc/init/init.leo.camera.rc');info.uid=info.gid=0;info.mode=0o644
 with source.open('rb') as stream:tar.addfile(info,stream)
if not args.install:print('Prepared',len(files),'private runtime files. Explicit --install required.');raise SystemExit(0)
assert not native('/system/bin/sh','-c','test -e '+target+' && echo exists || true'),'Runtime already exists; inspect before updating'
assert not native('/system/bin/sh','-c','test -e /system/etc/init/init.leo.camera.rc && echo exists || true'),'Init file already exists'
# Save baseline metadata locally; no existing system HAL/provider is overwritten.
before={'boot':native('/system/bin/cat','/proc/sys/kernel/random/boot_id'),'audio':native('/system/bin/getprop','sys.leo.audio.state'),'camera_enabled':native('/system/bin/getprop','persist.leo.camera.enabled'),'files':hashes,'init_sha256':sha(root/'init.leo.camera.rc')}
(out/'camera-runtime-before-install.json').write_text(json.dumps(before,indent=2))
subprocess.run(adb+['push',str(archive),'/tmp/camera-runtime-install.tar'],check=True,timeout=45)
assert native('/system/bin/sha256sum','/tmp/camera-runtime-install.tar').split()[0]==sha(archive)
native('/system/bin/sh','-c',"set -e; mount -o remount,rw /system; trap 'mount -o remount,ro /system' EXIT; mkdir -p "+target+"/hw; cp -a /vendor/lib/hw/. "+target+"/hw/; tar -xf /tmp/camera-runtime-install.tar -C /system; restorecon -R "+target+" /system/etc/init/init.leo.camera.rc; sync")
actual=native('/system/bin/sh','-c','cd '+target+' && sha256sum '+shlex.join(list(hashes)))
for line in actual.splitlines():
 digest,name=line.split(maxsplit=1);assert digest==hashes[name],name
assert native('/system/bin/sha256sum','/system/etc/init/init.leo.camera.rc').split()[0]==before['init_sha256']
assert ' /system ext4 ro,' in native('/system/bin/cat','/proc/mounts')
native('/system/bin/setprop','persist.leo.camera.enabled','1')
(out/'camera-runtime-installed.json').write_text(json.dumps({'files':hashes,'system_readonly':True,'boot_armed':True},indent=2))
print('Installed and verified',len(files),'private camera files; /system read-only. One reboot needed for init to load the new services.')
