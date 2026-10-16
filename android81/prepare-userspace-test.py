"""Extract only Android native runtime to a tar for volatile recovery chroot tests."""
from pathlib import Path
import gzip,hashlib,io,json,struct,tarfile
here=Path(__file__).resolve().parent; out=here/'output'
b=(out/'boot-ramdisk-probe.img').read_bytes()
assert hashlib.sha256(b).hexdigest()=='8efb73d20c085d731a07d55cfe4ed15bbe2bcf279c7da1ee714357d59bfe1b3f'
k,_,r,_,_,_,_,page=struct.unpack_from('<8I',b,8)
off=page+(k+page-1)//page*page;c=gzip.decompress(b[off:off+r]);i=0; selected={}
while True:
    assert c[i:i+6]==b'070701'
    f=[int(c[i+6+8*j:i+14+8*j],16) for j in range(13)]
    n=c[i+110:i+109+f[11]].decode();a=(i+110+f[11]+3)&~3
    if n=='TRAILER!!!':break
    assert not n.startswith('/') and '..' not in n.split('/')
    if n=='system' or n.startswith('system/'):
        selected[n]=(f,c[a:a+f[6]])
    i=(a+f[6]+3)&~3
assert all(n in selected for n in ('system/bin/sh','system/bin/linker','system/lib/libc.so'))
dest=out/'android81-userspace-test.tar.gz'
with tarfile.open(dest,'w:gz',format=tarfile.USTAR_FORMAT) as tar:
    for n,(f,body) in selected.items():
        t=tarfile.TarInfo(n);t.mode=f[1]&0o7777;t.uid=t.gid=0;t.mtime=0
        kind=f[1]&0o170000
        if kind==0o040000:t.type=tarfile.DIRTYPE;tar.addfile(t)
        elif kind==0o120000:
            t.type=tarfile.SYMTYPE;t.linkname=body.decode();tar.addfile(t)
        else:
            assert kind==0o100000;t.size=len(body);tar.addfile(t,io.BytesIO(body))
report={'archive':dest.name,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),
        'entries':len(selected),'uncompressed_file_bytes':sum(len(b) for f,b in selected.values()),
        'contains_init':False,'destination_on_phone':'/tmp/leo-android81-runtime',
        'persistent_partition_writes':False}
(out/'android81-userspace-test.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
