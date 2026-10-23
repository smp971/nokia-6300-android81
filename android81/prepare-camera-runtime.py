"""Stage private camera bring-up files; never replace the live system HAL."""
from pathlib import Path
import argparse,hashlib,json,shlex,subprocess,tarfile
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--provider-only",action="store_true");args=parser.parse_args()
root=Path(__file__).resolve().parent;stock=root.parent/'analysis/system';out=root/'output'
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d'];base='/data/local/tmp/leo-camera-integration'
def native(*cmd):return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*cmd])],timeout=30).decode(errors='replace').strip()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
if not args.provider_only:
 assert not native('/system/bin/sh','-c','pidof mm-qcamera-daemon camera-provider || true'),'Stop previous camera bring-up processes first'
else:
 assert not native('/system/bin/sh','-c','pidof camera-provider || true'),'Provider already running'
if native('/system/bin/sh','-c','test -d '+base+' && echo yes || true')=='yes':assert native('/system/bin/cat',base+'/.owner')=='Leo camera integration v1'
else:
 native('/system/bin/mkdir','-p',base+'/hw',base+'/lib',base+'/sockets')
 native('/system/bin/sh','-c',"printf 'Leo camera integration v1' > "+base+'/.owner')
 native('/system/bin/cp','-a','/vendor/lib/hw/.',base+'/hw/')
files={'mm-qcamera-daemon':stock/'bin/mm-qcamera-daemon','exec':out/'leo-camera-runtime-exec'}
audit=json.loads((out/'camera-port-audit.json').read_text())
for row in audit['libraries']:
 name=row['name']
 if row['source']!='kaios-private' or not name.endswith('.so'):continue
 files['lib/'+name]=next(p for p in [stock/'lib'/name,stock/'vendor/lib'/name,stock/'lib/hw'/name] if p.is_file())
for p in (stock/'vendor/lib').glob('*.so'):
 if p.name.startswith(('libmmcamera','libchromatix','libactuator')):files['lib/'+p.name]=p
for name in ['libC2D2.so', 'libc2d30-a3xx.so', 'libc2d30-a4xx.so', 'libqomx_jpegenc.so', 'libqomx_jpegdec.so', 'libmmqjpeg_codec.so', 'libmmjpeg.so', 'libfastcvopt.so', 'libjpegehw.so', 'libjpegdhw.so']:
 files['lib/'+name]=stock/'vendor/lib'/name
files['hw/camera.msm8909.so']=stock/'lib/hw/camera.msm8909.so'
files['hw/camera.qcom.so']=out/'camera.qcom.so'
aosp=Path('//wsl.localhost/kaios/root/android81-leo/src/out/target/product/leo/system')
provider=aosp/'vendor/bin/hw/android.hardware.camera.provider@2.4-service'
if provider.is_file():
 files['camera-provider']=provider
 for name in ['android.hardware.camera.provider@2.4-impl.so','camera.device@1.0-impl.so','camera.device@3.2-impl.so','camera.device@3.3-impl.so']:
  source=next(p for p in [aosp/'vendor/lib/hw'/name,aosp/'vendor/lib'/name,aosp/'lib'/name] if p.is_file())
  files[('hw/' if '-impl.so' in name and name.startswith('android.hardware') else 'lib/')+name]=source
if args.provider_only:
 assert provider.is_file(),'Build the provider first'
 files={k:v for k,v in files.items() if k in ['camera-provider','hw/camera.qcom.so'] or 'camera.device@' in k or 'android.hardware.camera.provider@2.4-impl' in k}
archive=out/'camera-integration-private.tar';digests={}
with tarfile.open(archive,'w') as tar:
 for name,source in files.items():
  assert source.is_file() and not source.is_symlink()
  digests[name]=hashlib.sha256(source.read_bytes()).hexdigest();info=tar.gettarinfo(str(source),name);info.uid=info.gid=0;info.mode=0o755 if '/' not in name else 0o644
  with source.open('rb') as stream:tar.addfile(info,stream)
subprocess.run(adb+['push',str(archive),'/tmp/camera-integration-private.tar'],check=True,timeout=45)
assert native('/system/bin/sha256sum','/tmp/camera-integration-private.tar').split()[0]==hashlib.sha256(archive.read_bytes()).hexdigest()
native('/system/bin/tar','-xf','/tmp/camera-integration-private.tar','-C',base)
actual=native('/system/bin/sh','-c','cd '+base+' && sha256sum '+shlex.join(list(digests)))
for line in actual.splitlines():
 h,name=line.split(maxsplit=1);assert h==digests[name],name
(out/'camera-integration-staged.json').write_text(json.dumps({'private_only':True,'files':digests,'provider_available':provider.is_file()},indent=2))
print('Verified private files:',len(files),'Provider:',provider.is_file())
