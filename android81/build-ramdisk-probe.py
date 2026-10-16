"""Build a boot-only Android init/ADB probe; never writes to a device.

Embed the minimal native runtime in the initramfs, omit all persistent mounts.
Run in WSL kaios after the diagnostic build succeeds.
"""
from pathlib import Path
import gzip
import hashlib
import json
import re
import struct
import subprocess

HERE=Path(__file__).resolve().parent
SYSTEM=Path('/root/android81-leo/src/out/target/product/leo/system')
source=HERE/'output/boot.img'
data=source.read_bytes()
assert data[:8]==b'ANDROID!'
ksz,ka,rsz,ra,ssz,sa,tags,page=struct.unpack_from('<8I',data,8)
assert ssz==0 and page==2048
up=lambda n:(n+page-1)//page*page
kernel=data[page:page+ksz]
ramdisk=gzip.decompress(data[page+up(ksz):page+up(ksz)+rsz])
entries={}
i=0
while True:
    assert ramdisk[i:i+6]==b'070701'
    f=[int(ramdisk[i+6+8*k:i+14+8*k],16) for k in range(13)]
    name=ramdisk[i+110:i+110+f[11]-1].decode()
    off=(i+110+f[11]+3)&~3
    if name=='TRAILER!!!':
        break
    entries[name]=(f,ramdisk[off:off+f[6]])
    i=(off+f[6]+3)&~3

def put(name,body,mode):
    for parent in reversed(Path(name).parents):
        key=parent.as_posix()
        if key!='.' and key not in entries:
            entries[key]=([0,0o40755,0,0,2,0,0,0,0,0,0,0,0],b'')
    entries[name]=([0,mode,0,0,1,0,len(body),0,0,0,0,0,0],body)

# Replace the hardware init with a version that mounts only volatile /data.
put('init.qcom.rc',b'''import /init.leo.usb.rc
on fs
    mount tmpfs tmpfs /data mode=0771,uid=1000,gid=1000
on post-fs-data
    mkdir /data/misc 01771 system misc
    mkdir /data/misc/adb 02750 system shell
    mkdir /data/local 0751 root root
    mkdir /data/local/tmp 0771 shell shell
on boot
    setprop sys.usb.configfs 1
    setprop sys.usb.config adb
''',0o100644)
put('fstab.qcom',b'# Boot-only probe: no persistent block devices mounted.\n',0o100644)
added=set()
def add(path):
    relative=path.relative_to(SYSTEM).as_posix()
    if relative in added:
        return
    added.add(relative)
    if path.is_symlink():
        target=path.readlink().as_posix()
        put('system/'+relative,target.encode(),0o120777)
        linked=(SYSTEM/target.removeprefix('/system/')) if target.startswith('/system/') else path.parent/target
        add(linked)
        return
    assert path.is_file(), path
    body=path.read_bytes()
    put('system/'+relative,body,0o100755 if relative.startswith('bin/') else 0o100644)
    if body[:4]==b'\x7fELF':
        r=subprocess.run(['readelf','-d',str(path)],capture_output=True,text=True)
        for dep in re.findall(r'\(NEEDED\).*?\[(.*?)\]',r.stdout):
            add(SYSTEM/'lib'/dep)

for binary in ('adbd','sh','toybox','toolbox','linker','logcat','logd','getprop','setprop','ls','cat','id','dmesg'):
    path=SYSTEM/'bin'/binary
    if path.exists() or path.is_symlink():
        add(path)
for name in ('build.prop','etc/ld.config.txt','etc/hosts'):
    path=SYSTEM/name
    if path.is_file():
        put('system/'+name,path.read_bytes(),0o100644)
assert 'bin/adbd' in added and 'bin/sh' in added and 'bin/linker' in added

out=bytearray()
for number,(name,(fields,body)) in enumerate(list(entries.items())+[('TRAILER!!!',([0,0,0,0,1,0,0,0,0,0,0,0,0],b''))],1):
    fields=list(fields)
    fields[0]=number
    fields[6]=len(body)
    encoded=name.encode()+b'\0'
    fields[11]=len(encoded)
    out+=b'070701'+b''.join(f'{v:08x}'.encode() for v in fields)+encoded
    out+=b'\0'*((-len(out))%4)
    out+=body
    out+=b'\0'*((-len(out))%4)
out+=b'\0'*((-len(out))%512)
packed=gzip.compress(bytes(out),compresslevel=9,mtime=0)
header=bytearray(data[:page])
struct.pack_into('<I',header,16,len(packed))
sha=hashlib.sha1()
for blob in (kernel,packed,b''):
    sha.update(blob)
    sha.update(struct.pack('<I',len(blob)))
header[576:596]=sha.digest()
image=bytes(header)+kernel+b'\0'*(up(ksz)-ksz)+packed+b'\0'*(up(len(packed))-len(packed))
assert len(image)<=33554432
destination=HERE/'output/boot-ramdisk-probe.img'
destination.write_bytes(image)
report={'image':destination.name,'bytes':len(image),'sha256':hashlib.sha256(image).hexdigest(),
        'kernel_sha256':hashlib.sha256(kernel).hexdigest(),'embedded_files':sorted(added),
        'ramdisk_uncompressed_bytes':len(out),'ramdisk_compressed_bytes':len(packed),
        'persistent_partitions_mounted':[],'status':'offline preparation only; hardware boot not tested'}
(HERE/'output/ramdisk-probe.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
