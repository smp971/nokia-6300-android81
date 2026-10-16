"""Verify and inspect the 2780 donor offline; never execute donor programs."""
from pathlib import Path
import argparse
import hashlib
import json
import lzma
import re
import shutil
import struct
import subprocess

WORKSPACE=Path(__file__).resolve().parent.parent
HERE=WORKSPACE/'kaios31'
SCRATCH=Path('/root/kaios31-leo')
SCRATCH.mkdir(exist_ok=True)
parser=argparse.ArgumentParser()
parser.add_argument('--reuse-extracted',action='store_true')
args=parser.parse_args()
release=json.loads((HERE/'donor/release.json').read_text(encoding='utf-8-sig'))
asset=next(a for a in release['assets'] if a['name']=='system-patched.img.xz')
archive=HERE/'donor'/asset['name']
digest=hashlib.sha256(archive.read_bytes()).hexdigest()
assert archive.stat().st_size==asset['size'], 'Download size mismatch'
assert 'sha256:'+digest==asset['digest'], 'Release digest mismatch'
image=SCRATCH/'system.img'
if not args.reuse_extracted:
    with lzma.open(archive,'rb') as src, image.open('wb') as dst:
        shutil.copyfileobj(src,dst,4*1024*1024)
with image.open('rb') as f:
    magic=f.read(4)
assert magic != struct.pack('<I',0xed26ff3a), 'Sparse image: convert before debugfs extraction'
with image.open('rb') as f:
    f.seek(1024)
    sb=f.read(1024)
assert sb[56:58]==b'\x53\xef', 'Expected ext4 image'
block_size=1024 << struct.unpack_from('<I',sb,24)[0]
blocks=struct.unpack_from('<I',sb,4)[0]
free=struct.unpack_from('<I',sb,12)[0]
extract=SCRATCH/'extracted'
extract.mkdir(exist_ok=True)
if not args.reuse_extracted:
    log=subprocess.run(['debugfs','-R',f'rdump / {extract}',str(image)],capture_output=True,text=True)
    (HERE/'donor/extraction.log').write_text(log.stdout+log.stderr)
    assert log.returncode==0, 'debugfs failed'
system_as_root=(extract/'system/build.prop').exists()
if system_as_root:
    extract=extract/'system'

def elf(path):
    if not path.is_file() and path.parent.name=='lib':
        for candidate in (extract/'apex').glob('*/lib/bionic/'+path.name):
            if candidate.is_file() and not candidate.is_symlink():
                path=candidate
                break
    if not path.is_file():
        return None
    with path.open('rb') as f:
        h=f.read(20)
    if h[:4]!=b'\x7fELF':
        return None
    result={'class':{1:'ELF32',2:'ELF64'}.get(h[4]),'machine':struct.unpack_from('<H',h,18)[0]}
    r=subprocess.run(['readelf','-d',str(path)],capture_output=True,text=True)
    result['needed']=re.findall(r'\(NEEDED\).*?\[(.*?)\]',r.stdout)
    return result

def props(path):
    if not path.exists():
        return {}
    return dict(line.split('=',1) for line in path.read_text(errors='replace').splitlines()
                if line and not line.startswith('#') and '=' in line)

dp=props(extract/'build.prop')
stock=WORKSPACE/'analysis/system'
sp=props(stock/'build.prop')
selected=('ro.build.version.release','ro.build.version.sdk','ro.build.version.base_os',
          'ro.build.version.incremental','ro.product.cpu.abilist','ro.board.platform','ro.product.model')
runtime={}
for name in ('b2g/b2g','b2g/libxul.so','b2g/libmozglue.so','bin/api-daemon','kaios/api-daemon',
             'lib/libc.so','lib/libbinder.so','lib/libgui.so','lib/hw/hwcomposer.msm8909.so'):
    runtime[name]={'donor':elf(extract/name),'stock':elf(stock/name)}
ini={}
for name in ('b2g/application.ini','b2g/platform.ini'):
    p=extract/name
    if p.exists():
        ini[name]=p.read_text(errors='replace')
        (HERE/'donor'/p.name).write_text(ini[name])
report={'release':release['tag_name'],'download_url':asset['browser_download_url'],'sha256':digest,
        'community_patched':True,'system_as_root':system_as_root,'image_bytes':image.stat().st_size,
        'ext4_block_size':block_size,'ext4_total_bytes':blocks*block_size,
        'ext4_allocated_bytes_approx':(blocks-free)*block_size,
        'nokia6300_system_partition_bytes':838860800,
        'properties':{'donor':{k:dp.get(k) for k in selected},'stock':{k:sp.get(k) for k in selected}},
        'runtime_elf':runtime,'ini':ini,'extracted_path':str(extract),
        'scope':'Offline analysis only; DT_NEEDED presence does not prove ABI compatibility.'}
(HERE/'donor-analysis.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
