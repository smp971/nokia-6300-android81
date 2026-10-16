"""Stage new image files in a dedicated SD folder; do not format or flash partitions."""
from pathlib import Path
import hashlib,json,subprocess
HERE=Path(__file__).resolve().parent;OUT=HERE/'output'
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d'];BB='/system/bin/busybox '
FOLDER='/tmp/leo-sd/leo-android81'
OWNER='LEO_ANDROID81_DIAGNOSTIC_695d'
def call(*args,timeout=60):
    result=subprocess.run(ADB+list(args),capture_output=True,text=True,timeout=timeout)
    if result.returncode:raise RuntimeError(result.stderr or result.stdout)
    return result.stdout.replace('\r','').strip()
def sha256(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1048576),b''):h.update(chunk)
    return h.hexdigest()
assert 'uid=0' in call('shell',BB+'id')
assert call('shell',BB+'cat /tmp/leo-stage')=='android-init-boot-action'
assert call('shell',BB+'cat /sys/class/block/mmcblk1/device/type')=='SD'
assert call('shell',BB+'cat /sys/class/block/mmcblk1p1/dev')=='179:65'
assert 'androidboot.serialno=695d' in call('shell',BB+'cat /proc/cmdline')
mounts=call('shell',BB+'cat /proc/mounts')
if ' /tmp/leo-sd vfat ' not in mounts:
    call('shell',BB+'mkdir -p /tmp/leo-sd')
    call('shell','if [ ! -b /dev/leo-sd ]; then '+BB+'mknod /dev/leo-sd b 179 65; fi')
    call('shell',BB+'mount -t vfat -o ro /dev/leo-sd /tmp/leo-sd')
owner=call('shell','if [ -e '+FOLDER+'/owner ]; then '+BB+'cat '+FOLDER+'/owner; elif [ -d '+FOLDER+' ]; then echo UNKNOWN_EXISTING_FOLDER; fi')
assert owner in ('',OWNER),'Existing folder is not identified as this experiment'
metadata=json.loads((OUT/'framework-runtime.json').read_text())
sources={}
for name,info in metadata['images'].items():
    assert name in ('system.raw.img','data.raw.img')
    transfer=info.get('compressed',info)
    source=Path('\\\\wsl.localhost\\kaios'+transfer['path'].replace('/','\\'))
    assert source.stat().st_size==transfer['bytes'] and sha256(source)==transfer['sha256']
    sources[name]=source
call('shell',BB+'mount -o remount,rw /tmp/leo-sd')
call('shell',BB+'mkdir -p '+FOLDER)
if not owner:call('shell',"echo "+OWNER+' > '+FOLDER+'/owner')
result={'target_serial':'695d','directory':FOLDER,'partition_formatting':False,'images':{}}
try:
    for name,info in metadata['images'].items():
        dest=FOLDER+'/'+name
        exists=call('shell','if [ -f '+dest+' ]; then echo yes; fi')=='yes'
        if exists:
            actual=call('shell',BB+'sha256sum '+dest,timeout=180).split()[0]
            # Identical untouched files can be reused after an interrupted transfer.
            # DATA is mutable after boot: a changed image is always retained.
            assert actual==info['sha256'], 'Existing image retained: '+dest
            result['images'][name]={'sha256':actual,'bytes':info['bytes'],'existing_hash_verified':True};continue
        print('Transferring new SD image file:',name,flush=True)
        incoming=dest+'.incoming'
        if 'compressed' in info:
            call('push',str(sources[name]),incoming+'.gz',timeout=900)
            call('shell',BB+'gunzip -c '+incoming+'.gz > '+incoming,timeout=900)
        else:call('push',str(sources[name]),incoming,timeout=900)
        actual=call('shell',BB+'sha256sum '+incoming,timeout=180).split()[0]
        assert actual==info['sha256'],'SD image checksum mismatch'
        call('shell',BB+'mv '+incoming+' '+dest)
        if 'compressed' in info:call('shell',BB+'rm '+incoming+'.gz')
        result['images'][name]={'sha256':actual,'bytes':info['bytes']}
    call('shell',BB+'sync')
finally:
    call('shell',BB+'mount -o remount,ro /tmp/leo-sd')
(OUT/'framework-stage-result.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
