"""Build a private standalone Leo Recovery candidate; never contacts the phone.

Native installation accepts only the exact verified ZIPs pinned into this build.
No stock signature checks are disabled: this is a separate custom recovery UI.
"""
from pathlib import Path
import argparse, hashlib, importlib.util, json, shutil, stat, struct, subprocess, zipfile
import recovery_image
import kaios_sd_ramdisk

ROOT=Path(__file__).resolve().parent
BASE_SHA='2e4dee03305c5b617d5b0d51c064af350c817f3dc93f669ec302bdd97adf6fdc'
spec=importlib.util.spec_from_file_location('bundle',ROOT/'install-recovery-bundle.py')
bundle=importlib.util.module_from_spec(spec);spec.loader.exec_module(bundle)

def sha(path):
    with path.open('rb') as source:return bundle.digest_stream(source)

def linux(path):
    path=path.resolve().as_posix();return '/mnt/'+path[0].lower()+path[2:]

def recovery_policy(policy):
    # The stock recovery domain cannot inspect PID 1 mounts or create our block
    # aliases. Keep global enforcing and every vendor rule; permit only this
    # offline recovery domain, whose ID was read with libsepol from this policy.
    if hashlib.sha256(policy).hexdigest()!='a99d1deab34fb87a1d24406f6ab53e1fe8d5ff549ed371973820a0560c4828c4':
        raise ValueError('Unrecognized recovery policy; do not guess domain IDs')
    return kaios_sd_ramdisk.diagnostic_domains(policy,(239,))

def zip_descriptor(path,name,expected_bytes):
    # The WHOLE ZIP is pinned before any native extraction. Pin its data offsets
    # too: Oreo FindEntry rejects Python's ZIP64 local header above 2 GiB, even
    # when both actual lengths fit its uint32 ZipEntry API.
    with zipfile.ZipFile(path) as archive:
        info=archive.getinfo(name)
        archive.fp.seek(info.header_offset);raw=archive.fp.read(30)
        if len(raw)!=30:raise ValueError('Truncated local ZIP header')
        header=struct.unpack('<IHHHHHIIIHH',raw)
        if header[0]!=0x04034b50 or header[2]!=info.flag_bits or header[3]!=info.compress_type:
            raise ValueError('Inconsistent local ZIP header')
        if info.flag_bits&9 or info.compress_type not in (0,8) or info.file_size!=expected_bytes:
            raise ValueError('Unsupported image ZIP entry')
        offset=info.header_offset+30+header[9]+header[10]
        if info.file_size>0xffffffff or info.compress_size>0xffffffff or offset+info.compress_size>archive.start_dir:
            raise ValueError('Image outside supported ZIP bounds')
        return (offset,info.compress_size,info.file_size,info.CRC,info.compress_type)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fresh',type=Path,default=ROOT/'output/recovery-bundles/final/leo-fresh-0.14.0-dev-20261027-232615.zip')
    parser.add_argument('--stock',type=Path,default=ROOT/'output/recovery-entry-kit/kaios-original-restore-695d.zip')
    parser.add_argument('--package-only',action='store_true',help='Reuse an already compiled native binary')
    args=parser.parse_args();out=ROOT/'output/leo-recovery';out.mkdir(exist_ok=True)
    catalog={};packed=[];header=['#pragma once','#include "policy.h"','using leo::Image;using leo::Bundle;']
    for symbol,path,title,schema in [('Fresh',args.fresh,'Leo 0.14 / clean',2),('Stock',args.stock,'KaiOS original',1)]:
        meta=bundle.verify(path)
        if meta['schema']!=schema:raise ValueError('Unexpected package schema')
        digest=sha(path);names=bundle.flash_order(meta);items=[]
        header.append('static const Image k'+symbol+'Images[]={')
        for name in names:
            number,size=bundle.image_parts(meta)[name];major,minor=(259,7) if name=='userdata' else (179,number)
            image_sha=meta['files']['images/'+name+'.img']['sha256']
            header.append('{'+json.dumps(name)+','+f'{number},{major},{minor},{size}ULL,'+json.dumps(image_sha)+'},')
            items.append({'name':name,'bytes':size,'sha256':image_sha})
            packed.append((digest,name,zip_descriptor(path,'images/'+name+'.img',size)))
        header.append('};')
        header.append('static const Bundle k'+symbol+'={'+','.join([json.dumps(title),json.dumps(path.name),json.dumps(digest),str(path.stat().st_size)+'ULL','true' if schema==2 else 'false','k'+symbol+'Images',str(len(names))])+'};')
        catalog[symbol.lower()]={'path':str(path.resolve()),'filename':path.name,'sha256':digest,'bytes':path.stat().st_size,'images':items}
    header+=['struct PinnedZipImage {const char* archiveSha;const char* name;uint64_t offset;uint32_t compressed,uncompressed,crc;uint16_t method;};','static const PinnedZipImage kZipImages[]={']
    for archive_sha,name,values in packed:
        header.append('{'+','.join([json.dumps(archive_sha),json.dumps(name),str(values[0])+'ULL']+[str(x)+'U' for x in values[1:]])+'},')
    header+=['};','struct Original {const char* filename;uint64_t bytes;const char* sha;};','static const Original kOriginals[]={']
    for name,(size,digest) in bundle.STOCK_BACKUPS.items():header.append('{'+','.join([json.dumps(name),str(size)+'ULL',json.dumps(digest)])+'},')
    header+=['};','static const Image kBackupImages[]={',
             '{"boot",29,179,29,33554432ULL,""},','{"system",30,179,30,838860800ULL,""},',
             '{"recovery",33,259,1,33554432ULL,""},','};']
    generated='\n'.join(header)+'\n';path=out/'catalog_generated.h'
    if args.package_only and path.read_text()!=generated:raise ValueError('Catalog changed; recompile native recovery')
    path.write_text(generated,newline='\n')
    native_sources=('Android.mk','recovery.cpp','framebuffer.cpp','rgb565.h','policy.h','selector.cpp')
    inputs={name:sha(ROOT/'leo-recovery'/name) for name in native_sources}
    inputs['catalog_generated.h']=sha(path)
    stamp=out/'native-build.json'
    if not args.package_only:
        subprocess.run(['wsl','-d','kaios','--','bash',linux(ROOT/'build-leo-recovery.sh')],check=True)
        if any(sha(ROOT/'leo-recovery'/name)!=inputs[name] for name in native_sources):
            raise ValueError('Native source changed during compilation')
        stamp.write_text(json.dumps({'inputs':inputs,'binary_sha256':sha(out/'recovery'),'selector_sha256':sha(out/'selector')},indent=2)+'\n')
    else:
        built=json.loads(stamp.read_text())
        if built['inputs']!=inputs or built['binary_sha256']!=sha(out/'recovery') or built.get('selector_sha256')!=sha(out/'selector'):
            raise ValueError('Native source/binary changed; compile before packaging')
    native=(out/'recovery').read_bytes();bundle.static_arm(native)
    base_path=ROOT/'output/recovery-debug.img'
    if sha(base_path)!=BASE_SHA:raise ValueError('Unrecognized root-recovery base')
    base=base_path.read_bytes();old=recovery_image.unpack(base);entries=old['entries'].copy()
    fields,policy=entries['sepolicy'];fields=fields.copy();policy=recovery_policy(policy)
    fields[6]=len(policy);entries['sepolicy']=(fields,policy)
    stock_path=ROOT/'backup-live/boot-device.img'
    if sha(stock_path)!=kaios_sd_ramdisk.STOCK_BOOT_SHA:raise ValueError('Unknown KaiOS boot')
    selector=(out/'selector').read_bytes();bundle.static_arm(selector)
    init_fields,init_body=entries['init']
    recovery_image.add(entries,'init-recovery',init_body,init_fields[1])
    replacement=init_fields.copy();replacement[6]=len(selector);entries['init']=(replacement,selector)
    recovery_image.add(entries,'sbin/leo-boot-selector',selector,stat.S_IFREG|0o755)
    recovery_image.add(entries,'kaios-root',b'',stat.S_IFDIR|0o755)
    for name,(fields,body) in kaios_sd_ramdisk.embedded(stock_path.read_bytes()).items():
        recovery_image.add(entries,'kaios-root/'+name,body,fields[1]);entries['kaios-root/'+name][0][2:4]=fields[2:4]
    if 'sbin/recovery-stock' in entries:raise ValueError('Unexpected recovery base')
    fields,original=entries['sbin/recovery']
    recovery_image.add(entries,'sbin/recovery-stock',original,stat.S_IFREG|0o755)
    updated=fields.copy();updated[1]=stat.S_IFREG|0o755;updated[2]=updated[3]=0;updated[6]=len(native)
    entries['sbin/recovery']=(updated,native)
    font='res/images/font.png'
    if font in entries:
        fields,_=entries[font];new=fields.copy();new[6]=(out/'font.png').stat().st_size;entries[font]=(new,(out/'font.png').read_bytes())
    else:recovery_image.add(entries,font,(out/'font.png').read_bytes(),stat.S_IFREG|0o644)
    recovery_image.add(entries,'leo-recovery-catalog.json',json.dumps(catalog,indent=2).encode(),stat.S_IFREG|0o644)
    recovery_image.add(entries,'leo-recovery-font-OFL.txt',(out/'OFL.txt').read_bytes(),stat.S_IFREG|0o644)
    contexts='file_contexts'
    if contexts in entries and b'\0' not in entries[contexts][1]:
        fields,data=entries[contexts];data+=b'\n/sbin/recovery-stock u:object_r:recovery_exec:s0\n'
        fields=fields.copy();fields[6]=len(data);entries[contexts]=(fields,data)
    result=recovery_image.repack(base,entries,Path('D:/ROMS/edl-tools/recovery-8110.img').read_bytes())
    image=out/'leo-recovery-0.2.img';image.write_bytes(result)
    unchanged=[name for name,value in old['entries'].items() if name not in ('init','sbin/recovery','sepolicy',font,contexts)]
    verified=recovery_image.unpack(result)['entries']
    if any(verified[name]!=old['entries'][name] for name in unchanged):raise ValueError('Unrelated ramdisk entry changed')
    report={'version':'0.2-sd-boot','serial':'695d','bytes':len(result),'sha256':sha(image),
            'source_commit':subprocess.check_output(['git','-C',str(ROOT.parent),'rev-parse','HEAD'],text=True).strip(),
            'working_tree_dirty':bool(subprocess.check_output(['git','-C',str(ROOT.parent),'status','--porcelain'])),
            'kernel_unchanged':True,'original_recovery_init_preserved':True,'adbd_unchanged':True,'native_static_arm':True,
            'selector_sha256':sha(out/'selector'),'kaios_sd_selector_implemented':True,'kaios_sd_boot_validated':False,
            'native_sha256':sha(out/'recovery'),'base_sha256':BASE_SHA,'catalog':catalog,
            'auto_execute_ota_commands':False,'hardware_boot_validated':False,'hardware_install_validated':False,
            'phone_written':False,'native_source_sha256':sha(ROOT/'leo-recovery/recovery.cpp'),'native_build_inputs':inputs}
    entry_spec=importlib.util.spec_from_file_location('entry',ROOT/'install-root-recovery.py')
    entry=importlib.util.module_from_spec(entry_spec);entry_spec.loader.exec_module(entry)
    if report['sha256']!=entry.LEO_SHA:raise ValueError('Review the new image and update the explicit LEO_SHA installer pin before packaging')
    evidence_path=ROOT/'leo-recovery/hardware-validation.json'
    evidence=json.loads(evidence_path.read_text()) if evidence_path.exists() else None
    if evidence and evidence.get('image_sha256')==report['sha256']:
        report['hardware_validation']=evidence
        report['hardware_boot_validated']=evidence.get('direct_boot_validated') is True
        report['hardware_install_validated']=evidence.get('rom_install_validated') is True
        report['phone_written']=evidence.get('recovery_partition_verified') is True
    report['build_writes_phone']=False
    sd_evidence_path=ROOT/'leo-recovery/sd-boot-validation.json'
    sd_evidence=json.loads(sd_evidence_path.read_text()) if sd_evidence_path.exists() else None
    if sd_evidence and sd_evidence.get('image_sha256')==report['sha256']:
        report['kaios_sd_validation']=sd_evidence
        report['kaios_sd_boot_validated']=sd_evidence.get('kaios_ui_boot_user_confirmed') is True
    report['build_tool_sha256']={name:sha(ROOT/name) for name in ('build-leo-recovery.py','build-leo-recovery.sh','recovery_image.py','kaios_sd_ramdisk.py','install-root-recovery.py','leo-recovery/policy.h')}
    (out/'manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    shutil.copyfile(ROOT/'install-root-recovery.py',out/'install-root-recovery.py')
    archive=out/'leo-recovery-0.2-private.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as package:
        package.write(image,image.name);package.write(out/'manifest.json','manifest.json');package.write(out/'OFL.txt','OFL.txt')
        package.write(ROOT/'install-root-recovery.py','install-root-recovery.py')
        package.write(ROOT/'LEO-RECOVERY.md','README.md');package.write(ROOT/'KAIOS-SD.md','KAIOS-SD.md')
        package.write(ROOT/'screenshots/leo-recovery-02-sd-menu.png','screenshots/leo-recovery-02-sd-menu.png')
    with zipfile.ZipFile(archive) as package:
        with package.open(image.name) as stream:
            if bundle.digest_stream(stream)!=report['sha256']:raise ValueError('Recovery ZIP readback mismatch')
    print(json.dumps({'image':str(image),'image_sha256':report['sha256'],'archive':str(archive),'archive_sha256':sha(archive),'hardware_boot_validated':report['hardware_boot_validated']},indent=2),flush=True)

if __name__=='__main__':main()
