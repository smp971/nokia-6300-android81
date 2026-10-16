"""No-content-change recovery control: preserve raw CPIO, recompress gzip.

Preserve the stock header/addresses/cmdline/OS fields and exact kernel payload.
Never flashes; produces an exact partition-sized image and audit report.
"""
from pathlib import Path
import gzip
import hashlib
import json
import struct

HERE=Path(__file__).resolve().parent
OUT=HERE/'output'
original=(HERE/'backup-live/recovery-device.img').read_bytes()
saved=json.loads((HERE/'backup-live/checksums.json').read_text())['recovery']
assert hashlib.sha256(original).hexdigest()==saved['sha256']
assert original[:8]==b'ANDROID!'
k,ka,r,ra,s,sa,tags,page,hv,osv=struct.unpack_from('<10I',original,8)
assert s==0 and hv==0 and page==2048
up=lambda n:(n+page-1)//page*page
kernel=original[page:page+k]
offset=page+up(k)
cpio=gzip.decompress(original[offset:offset+r])
old_end=offset+up(r)
# The saved partition includes nonzero data after the declared image payload.
# Its purpose is not established. Preserve it at its original absolute offsets.
packed=gzip.compress(cpio,compresslevel=9,mtime=1)
assert up(len(packed))<=up(r), 'Repacked ramdisk would overlap unexplained trailing data'
assert gzip.decompress(packed)==cpio
header=bytearray(original[:page])
struct.pack_into('<I',header,16,len(packed))
sha=hashlib.sha1()
for payload in (kernel,packed,b''):
    sha.update(payload)
    sha.update(struct.pack('<I',len(payload)))
header[576:596]=sha.digest()
image=bytearray(original)
image[:page]=header
image[offset:old_end]=bytes(old_end-offset)
image[offset:offset+len(packed)]=packed
image=bytes(image)
assert image[old_end:]==original[old_end:]
# Only ramdisk size and image ID may differ in the header.
allowed=set(range(16,20))|set(range(576,596))
changes=[i for i,(a,b) in enumerate(zip(original[:page],image[:page])) if a!=b]
assert all(i in allowed for i in changes)
assert image[page:page+k]==kernel
assert image!=original, 'Control must actually exercise different image packaging'
destination=OUT/'recovery-control-repacked.img'
destination.write_bytes(image)
report={'source_recovery_sha256':saved['sha256'],'bytes':len(image),
        'md5':hashlib.md5(image).hexdigest(),'sha256':hashlib.sha256(image).hexdigest(),
        'kernel_unchanged':True,'cpio_bytes_unchanged':True,
        'cpio_sha256':hashlib.sha256(cpio).hexdigest(),
        'gzip_original_bytes':r,'gzip_repacked_bytes':len(packed),
        'header_changed_offsets':changes,'os_version_field_preserved':osv,
        'trailing_data_preserved_at_original_offsets':True,
        'trailing_data_nonzero_bytes':sum(v!=0 for v in original[old_end:]),
        'status':'offline verified; awaiting recovery boot test'}
(OUT/'recovery-control.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
