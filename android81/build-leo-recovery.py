"""Build a private standalone Leo Recovery candidate; never contacts the phone.

Native installation accepts only the exact verified ZIPs pinned into this build.
No stock signature checks are disabled: this is a separate custom recovery UI.
"""
from pathlib import Path
import argparse, gzip, hashlib, importlib.util, json, shutil, stat, struct, subprocess, zipfile
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


def blank_image(raw_path, expected_size):
    with gzip.open(raw_path, 'rb') as source:
        header = source.read(1082)
        if header[1080:1082] != b'\x53\xef':
            raise ValueError(f'Invalid ext4 template: {raw_path.name}')
        size = len(header)
        digest = hashlib.sha256(header)
        for chunk in iter(lambda: source.read(1048576), b''):
            size += len(chunk)
            if size > expected_size:
                raise ValueError(f'Oversized SD template: {raw_path.name}')
            digest.update(chunk)
    if size != expected_size:
        raise ValueError(f'Truncated SD template: {raw_path.name}')
    return digest.hexdigest()


def add_leo_sd(entries, archive_path, catalog, assets, legacy_archive=None):
    images = {item['name']: item for item in catalog['images']}
    legacy_system = None
    if legacy_archive:
        legacy = bundle.verify(legacy_archive)
        if legacy['schema'] != 2 or legacy['files']['images/boot.img']['sha256'] != images['boot']['sha256']:
            raise ValueError('Previous Leo SD ROM must share the approved Leo boot image')
        legacy_system = legacy['files']['images/system.img']['sha256']
        if legacy_system == images['system']['sha256']:
            raise ValueError('Previous Leo SD ROM must provide a different System image')
    with zipfile.ZipFile(archive_path) as archive:
        boot = archive.read('images/boot.img')
    if hashlib.sha256(boot).hexdigest() != images['boot']['sha256']:
        raise ValueError('Approved Leo boot image changed')
    root = recovery_image.unpack(boot)['entries']
    internal = b'Validated internal Android partitions.\n'
    for name in ('init', 'init.android', 'sbin/leo-framework-mount', 'system/bin/sh',
                 'leo-framework-internal', 'leo-autostart-android', 'leo-framework-enabled',
                 'fstab.qcom', 'sbin/recovery'):
        if name not in root:
            raise ValueError(f'Missing Leo ramdisk entry: {name}')
    if root['leo-framework-internal'][1] != internal or b'LEO_DIAG_HOLD_ON_FAILURE' not in root['init.android'][1]:
        raise ValueError('Unexpected Leo ramdisk bootstrap')
    del root['leo-framework-internal']
    bootstrap = (assets / 'bootstrap').read_bytes()
    bundle.static_arm(bootstrap)
    if b'PID1 started for Leo SD; internal cache skipped' not in bootstrap:
        raise ValueError('Leo SD bootstrap missing cache isolation')
    mount_script = (ROOT / 'leo-recovery/leo-sd-mount.sh').read_bytes().replace(b'\r\n', b'\n')
    busybox = root['system/bin/sh'][1]
    bundle.static_arm(busybox)
    for name, content in (
        ('init', bootstrap), ('sbin/leo-framework-mount', mount_script),
        ('fstab.qcom', b'/dev/leo-android-loop0 /system ext4 ro,nodev,noload wait\n'
                       b'/dev/leo-android-loop1 /data ext4 nosuid,nodev wait\n'
                       b'/dev/leo-android-loop2 /cache ext4 nosuid,nodev,noexec wait\n'),
    ):
        fields = root[name][0].copy()
        fields[6] = len(content)
        root[name] = (fields, content)
    recovery_image.add(root,'sbin/busybox',busybox,stat.S_IFREG|0o755)
    radio_start = (ROOT / 'radio-runtime/start.sh').read_bytes().replace(b'\r\n', b'\n')
    recovery_image.add(root,'sbin/leo-sd-radio-start.sh',radio_start,stat.S_IFREG|0o644)
    if 'firmware' not in root:
        recovery_image.add(root,'firmware',b'',stat.S_IFDIR|0o755)
    recovery_image.add(root,'leo-sd',b'',stat.S_IFDIR|0o700)
    recovery_image.add(root,'leo-framework-sd-v1',b'Leo SD loop storage.\n',stat.S_IFREG|0o644)
    recovery_image.add(root,'leo-system.sha256',images['system']['sha256'].encode(),stat.S_IFREG|0o644)
    if legacy_system:
        recovery_image.add(root,'leo-system-legacy.sha256',legacy_system.encode(),stat.S_IFREG|0o644)
    recovery_image.add(root,'leo-templates',b'',stat.S_IFDIR|0o700)
    templates = {}
    for name, size in (('data',512*1024**2), ('cache',64*1024**2)):
        archive = assets / f'{name}.img.gz'
        digest = blank_image(archive, size)
        recovery_image.add(root,f'leo-templates/{name}.gz',archive.read_bytes(),stat.S_IFREG|0o644)
        recovery_image.add(root,f'leo-templates/{name}.sha256',digest.encode(),stat.S_IFREG|0o644)
        templates[name]={'bytes':size,'sha256':digest}
    recovery_image.add(entries,'leo-android-root',b'',stat.S_IFDIR|0o755)
    for name,(fields,body) in root.items():
        path='leo-android-root/'+name
        recovery_image.add(entries,path,body,fields[1])
        entries[path][0][2:4]=fields[2:4]
    return {'boot_source_sha256':images['boot']['sha256'],'system_sha256':images['system']['sha256'],
            'legacy_system_sha256':legacy_system,
            'bootstrap_sha256':hashlib.sha256(bootstrap).hexdigest(),
            'radio_start_sha256':hashlib.sha256(radio_start).hexdigest(),'templates':templates}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fresh',type=Path,default=ROOT/'output/recovery-bundles/final/leo-fresh-0.14.0-dev-20261027-232615.zip')
    parser.add_argument('--stock',type=Path,default=ROOT/'output/recovery-entry-kit/kaios-original-restore-695d.zip')
    parser.add_argument('--output',type=Path,default=ROOT/'output/leo-recovery')
    parser.add_argument('--candidate-only',action='store_true',help='Emit an offline candidate and report without packaging an installer')
    native_mode=parser.add_mutually_exclusive_group()
    native_mode.add_argument('--package-only',action='store_true',help='Reuse an already compiled native binary')
    native_mode.add_argument('--catalog-only',action='store_true',help='Generate the native header for a separate recovery build')
    parser.add_argument('--hardware-evidence',type=Path,default=ROOT/'leo-recovery/hardware-validation.json',
                        help='Hardware validation JSON for the exact image being packaged')
    parser.add_argument('--mtp-responder',type=Path,
                        help='Static ARM uMTP Responder binary for a local USB MTP candidate')
    parser.add_argument('--leo-sd-assets',type=Path,
                        help='Directory with the Leo SD bootstrap and empty data.img.gz and cache.img.gz')
    parser.add_argument('--legacy-leo-sd',type=Path,
                        help='Previously approved Leo clean ROM whose SD System stays bootable')
    args=parser.parse_args()
    if args.candidate_only and args.output.resolve() == (ROOT/'output/leo-recovery').resolve():
        parser.error('--candidate-only requires a separate --output directory')
    if args.mtp_responder and not args.candidate_only:
        parser.error('--mtp-responder currently requires --candidate-only')
    if args.legacy_leo_sd and not args.leo_sd_assets:
        parser.error('--legacy-leo-sd requires --leo-sd-assets')
    out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
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
    if args.catalog_only:
        print(f'Generated native catalog: {path}',flush=True)
        return
    native_sources=('Android.mk','recovery.cpp','framebuffer.cpp','rgb565.h','policy.h','selector.cpp')
    inputs={name:sha(ROOT/'leo-recovery'/name) for name in native_sources}
    inputs['catalog_generated.h']=sha(path)
    stamp=out/'native-build.json'
    if not args.package_only:
        subprocess.run(['wsl','-d','kaios','--','bash',linux(ROOT/'build-leo-recovery.sh'),linux(out)],check=True)
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
    leo_sd = add_leo_sd(entries,args.fresh,catalog['fresh'],args.leo_sd_assets,args.legacy_leo_sd) if args.leo_sd_assets else None
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
    mtp={}
    if args.mtp_responder:
        responder=args.mtp_responder.read_bytes()
        bundle.static_arm(responder)
        session=(ROOT/'leo-recovery/mtp-session.sh').read_bytes().replace(b'\r\n',b'\n')
        recovery_image.add(entries,'sbin/leo-mtp-responder',responder,stat.S_IFREG|0o755)
        recovery_image.add(entries,'sbin/leo-mtp-session',session,stat.S_IFREG|0o755)
        mtp={'responder_sha256':hashlib.sha256(responder).hexdigest(),
             'session_sha256':hashlib.sha256(session).hexdigest()}
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
    if mtp:
        report['usb_mtp']=mtp
    if leo_sd:
        report['leo_sd']=leo_sd
    entry_spec=importlib.util.spec_from_file_location('entry',ROOT/'install-root-recovery.py')
    entry=importlib.util.module_from_spec(entry_spec);entry_spec.loader.exec_module(entry)
    if args.candidate_only:
        report['build_writes_phone']=False
        report['installer_approved']=report['sha256'] in entry.LEO_SHAS
        (out/'manifest.json').write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps({'image':str(image),'image_sha256':report['sha256'],
                          'installer_approved':report['installer_approved']},indent=2),flush=True)
        return
    if report['sha256']!=entry.LEO_SHA:raise ValueError('Review the new image and update the explicit LEO_SHA installer pin before packaging')
    evidence_path=args.hardware_evidence
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
