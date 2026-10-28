"""Build an offline, recovery-only QFIL candidate for development Nokia 695d.

No USB access. No GPT, bootchain, userdata, radio or device-unique payloads.
The local programmer is pinned but EDL acceptance is NOT hardware validated.
"""
from pathlib import Path
import argparse, hashlib, importlib.util, json, struct, subprocess, zipfile, zlib
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
PROGRAMMER_SHA='8c9bb6a331657e0626358f68098ff1289cefa5fd8cbcaa928604835c983d56c4'
GEOMETRY={'boot':(29,309344,65536),'system':(30,374880,1638400),
          'recovery':(33,2488416,65536),'userdata':(39,2621440,5013471)}

def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def read_gpt(data):
    if len(data)<1024 or data[512:520]!=b'EFI PART':raise ValueError('Missing primary GPT')
    header=data[512:1024];length,crc=struct.unpack_from('<II',header,12)
    if not 92<=length<=512:raise ValueError('Invalid GPT header length')
    checked=bytearray(header[:length]);checked[16:20]=bytes(4)
    if zlib.crc32(checked)!=crc:raise ValueError('GPT header CRC mismatch')
    current=struct.unpack_from('<Q',header,24)[0]
    lba,count,size,array_crc=struct.unpack_from('<QIII',header,72)
    if current!=1 or lba!=2 or count!=40 or size!=128:raise ValueError('Unknown GPT layout')
    entries=data[lba*512:lba*512+count*size]
    if len(entries)!=count*size or zlib.crc32(entries)!=array_crc:raise ValueError('GPT array CRC mismatch')
    result={}
    for i in range(count):
        row=entries[i*size:(i+1)*size]
        if row[:16]==bytes(16):continue
        name=row[56:128].decode('utf-16le').split('\0')[0]
        first,last=struct.unpack_from('<QQ',row,32)
        if not name or name in result or last<first:raise ValueError('Invalid GPT entry')
        result[name]=(i+1,first,last-first+1)
    if any(result.get(name)!=geometry for name,geometry in GEOMETRY.items()):
        raise ValueError('Development Nokia partition geometry mismatch')
    return result

def rawprogram(geometry):
    if geometry!=GEOMETRY['recovery']:raise ValueError('Only exact recovery target is allowed')
    _,start,sectors=geometry
    root=ET.Element('data')
    ET.SubElement(root,'program',{
        'SECTOR_SIZE_IN_BYTES':'512','file_sector_offset':'0','filename':'leo-recovery.img',
        'label':'recovery','num_partition_sectors':str(sectors),'physical_partition_number':'0',
        'readbackverify':'false','size_in_KB':str(sectors//2),'sparse':'false',
        'start_byte_hex':hex(start*512),'start_sector':str(start)})
    return ET.tostring(root,encoding='utf-8',xml_declaration=True)+b'\n'

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'output/qfil-recovery/leo-qfil-recovery-695d-20261028.zip')
    parser.add_argument('--programmer',type=Path,default=ROOT.parent.parent/'nokia-6300-qfil-safe/8k.mbn')
    args=parser.parse_args()
    if args.output.exists():raise ValueError('Output exists; preserve the previous package')
    if sha(args.programmer)!=PROGRAMMER_SHA:raise ValueError('Unknown programmer')
    programmer=args.programmer.read_bytes()
    if programmer[:6]!=b'\x7fELF\x01\x01' or struct.unpack_from('<H',programmer,18)[0]!=40:
        raise ValueError('Expected ARM32 ELF programmer')
    gpt=ROOT.parent/'meta/gpt_main0.bin';geometry=read_gpt(gpt.read_bytes())
    spec=importlib.util.spec_from_file_location('entry',ROOT/'install-root-recovery.py')
    entry=importlib.util.module_from_spec(spec);spec.loader.exec_module(entry)
    image=ROOT/'output/leo-recovery/leo-recovery-0.2.img'
    if image.stat().st_size!=33554432 or sha(image)!=entry.LEO_SHA:raise ValueError('Unknown recovery image')
    evidence=json.loads((ROOT/'leo-recovery/hardware-validation.json').read_text())
    payload={'leo-recovery.img':image.read_bytes(),'8k.mbn':programmer,
             'rawprogram0.xml':rawprogram(geometry['recovery']),
             'patch0.xml':b'<?xml version="1.0"?>\n<patches/>\n',
             'README.md':(ROOT/'QFIL-RECOVERY.md').read_bytes()}
    manifest={'format':'leo-qfil-recovery-only','schema':1,'serial_reference':'695d',
              'device':'Nokia 6300 4G TA-1286','sector_bytes':512,
              'targets':['recovery'],'geometry':{'recovery':geometry['recovery']},
              'gpt_source_sha256':sha(gpt),'source_commit':subprocess.check_output(
                  ['git','-C',str(ROOT.parent),'rev-parse','HEAD'],text=True).strip(),
              'working_tree_dirty':bool(subprocess.check_output(['git','-C',str(ROOT.parent),'status','--porcelain'])),
              'programmer_edl_acceptance_validated':False,'qfil_flash_validated':False,
              'recovery_image_boot_validated':evidence.get('image_sha256')==sha(image) and evidence.get('direct_boot_validated') is True,
              'data_wipe':False,
              'files':{name:{'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()} for name,data in payload.items()}}
    payload['manifest.json']=(json.dumps(manifest,indent=2)+'\n').encode()
    payload['SHA256SUMS.txt']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(payload.items())).encode()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for name,data in payload.items():archive.writestr(name,data)
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None:raise ValueError('Package CRC failure')
        for name,data in payload.items():
            if archive.read(name)!=data:raise ValueError('Package readback mismatch')
    print(json.dumps({'archive':str(args.output),'sha256':sha(args.output),'qfil_flash_validated':False},indent=2))

if __name__=='__main__':main()
