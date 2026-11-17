"""Read GPT and extract only vendor from the public compressed donor dump."""
from pathlib import Path
import hashlib
import json
import lzma
import struct
import subprocess

HERE=Path(__file__).resolve().parent
SCRATCH=Path('/root/kaios31-leo')
release=json.loads((HERE/'donor/release-emmc.json').read_text(encoding='utf-8-sig'))
asset=next(a for a in release['assets'] if a['name']=='emmc.img.xz')
archive=HERE/'donor/emmc.img.xz'
assert archive.stat().st_size==asset['size']
sha=hashlib.sha256(archive.read_bytes()).hexdigest()
if asset.get('digest'):
    assert asset['digest']=='sha256:'+sha
with lzma.open(archive,'rb') as source:
    source.seek(512)
    header=source.read(512)
    assert header[:8]==b'EFI PART'
    entry_lba=struct.unpack_from('<Q',header,72)[0]
    count,entry_size=struct.unpack_from('<II',header,80)
    assert count<4096 and 128<=entry_size<=4096
    source.seek(entry_lba*512)
    entries=source.read(count*entry_size)
    parts=[]
    for i in range(count):
        e=entries[i*entry_size:(i+1)*entry_size]
        if e[:16]==bytes(16):
            continue
        first,last=struct.unpack_from('<QQ',e,32)
        name=e[56:128].decode('utf-16le').split('\0')[0]
        parts.append({'name':name,'start':first*512,'bytes':(last-first+1)*512})
    vendor=next(p for p in parts if p['name']=='vendor')
    source.seek(vendor['start'])
    remaining=vendor['bytes']
    with (SCRATCH/'vendor.img').open('wb') as dst:
        while remaining:
            chunk=source.read(min(4*1024*1024,remaining))
            assert chunk, 'Unexpected EOF'
            dst.write(chunk)
            remaining-=len(chunk)
dest=SCRATCH/'vendor-extracted'
dest.mkdir(exist_ok=True)
r=subprocess.run(['debugfs','-R',f'rdump / {dest}',str(SCRATCH/'vendor.img')],capture_output=True,text=True)
(HERE/'donor/vendor-extraction.log').write_text(r.stdout+r.stderr)
assert r.returncode==0
report={'sha256':sha,'download_url':asset['browser_download_url'],'partition_table':parts,
        'extracted_partition':vendor,'extracted_path':str(dest),
        'scope':'Only GPT metadata and vendor were extracted; device identity/NV/userdata partitions were not extracted.'}
(HERE/'vendor-analysis.json').write_text(json.dumps(report,indent=2))
print(json.dumps({'vendor':vendor,'vendor_prop_exists':(dest/'build.prop').exists()},indent=2))
