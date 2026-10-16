"""Verify image structure, stock kernel identity and expanded partition sizes."""
from pathlib import Path
import gzip
import hashlib
import json
import struct

HERE=Path(__file__).resolve().parent
OUT=HERE/'output'
boot=(OUT/'boot.img').read_bytes()
assert boot[:8]==b'ANDROID!'
ksz,ka,rsz,ra,ssz,sa,tags,page=struct.unpack_from('<8I',boot,8)
assert page==2048 and ssz==0
assert ka==0x80008000 and ra==0x81000000 and tags==0x80000100
assert len(boot)<=33554432
kernel=boot[page:page+ksz]
expected=json.loads((HERE/'port-inputs.json').read_text())['kernel_sha256']
assert hashlib.sha256(kernel).hexdigest()==expected
rd_offset=page+((ksz+page-1)//page)*page
rd=gzip.decompress(boot[rd_offset:rd_offset+rsz])
files={}
i=0
while True:
    assert rd[i:i+6] in (b'070701',b'070702')
    h=[int(rd[i+6+8*k:i+14+8*k],16) for k in range(13)]
    name=rd[i+110:i+110+h[11]-1].decode()
    offset=(i+110+h[11]+3)&~3
    files[name]=rd[offset:offset+h[6]]
    i=(offset+h[6]+3)&~3
    if name=='TRAILER!!!':
        break
required=('init','init.qcom.rc','init.leo.usb.rc','fstab.qcom','adb_keys')
assert all(name in files for name in required)
assert files['init'][:5]==b'\x7fELF\x01'
assert b'/userdata' not in files['fstab.qcom']
system=OUT/'system.img'
with system.open('rb') as f:
    h=f.read(28)
magic=struct.unpack_from('<I',h)[0]
if magic==0xed26ff3a:
    major,minor,header,chunk_header,block,blocks,chunks,checksum=struct.unpack_from('<HHHHIIII',h,4)
    assert major==1 and header>=28 and chunk_header>=12
    logical_size=block*blocks
    image_format='Android sparse'
else:
    logical_size=system.stat().st_size
    image_format='raw'
assert logical_size<=838860800
report={'boot_bytes':len(boot),'kernel_sha256':expected,'stock_kernel_unchanged':True,
        'ramdisk_required_files_present':list(required),
        'system_format':image_format,'system_file_bytes':system.stat().st_size,
        'system_expanded_bytes':logical_size,'system_partition_bytes':838860800,
        'scope':'Offline structural checks only; no hardware boot tested; native diagnostic target, not full Android Go.'}
(OUT/'verification.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
