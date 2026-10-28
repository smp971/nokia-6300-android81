"""Prepare private KaiOS SD storage on the PC; this is NOT a bootable SD ROM.

No phone/card access. Uses verified stock system, blank userdata/cache, and
exports proposed ramdisk mount changes for a future recovery boot selector.
"""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess
import recovery_image

ROOT=Path(__file__).resolve().parent
SYSTEM_SHA='a16d027dba2702fc9114ef3269770c30595a8e9b595329e5b7527588345fbdd6'
BOOT_SHA='a23fcbcc993a0f5f1f03736eb59d7e431604d9c2d8900f100b927c06a1c81338'

def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as source:
        for data in iter(lambda:source.read(1048576),b''):h.update(data)
    return h.hexdigest()

def linux(path):
    path=path.resolve().as_posix();return '/mnt/'+path[0].lower()+path[2:]

def mount_plan(text):
    rows=[]
    for line in text.splitlines():
        fields=line.split()
        if not fields or fields[0].startswith('#'):rows.append(line);continue
        if len(fields)!=5:raise ValueError('Unexpected stock fstab row')
        if fields[1]=='/system':fields=['/dev/block/loop0','/system','ext4','ro,noload,barrier=1','wait']
        elif fields[1]=='/data':fields=['/dev/block/loop1','/data','ext4','nosuid,nodev,noatime,barrier=1,noauto_da_alloc','wait']
        elif 'voldmanaged=sdcard' in fields[4]:
            rows.append('# SD is the OS backing store; do not expose it to vold formatting.');continue
        rows.append(' '.join(fields))
    result='\n'.join(rows)+'\n'
    if '/dev/block/bootdevice/by-name/system' in result or '/dev/block/bootdevice/by-name/userdata' in result or 'formattable' in result or 'encryptable=footer' in result:
        raise ValueError('Unsafe fallback remains in mount plan')
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,default=ROOT/'output/kaios-sd-preparation')
    args=parser.parse_args();out=args.output
    system=ROOT/'backup-live/kaios-before-internal/system-kaios.img';boot=ROOT/'backup-live/boot-device.img'
    if sha(system)!=SYSTEM_SHA or sha(boot)!=BOOT_SHA:raise ValueError('Stock backup mismatch')
    out.mkdir(parents=True,exist_ok=False);storage=out/'LeoKaiOS';storage.mkdir();plan=out/'ramdisk-plan';plan.mkdir()
    raw=boot.read_bytes();meta=recovery_image.unpack(raw)
    start=meta['offset'];(plan/'stock-ramdisk.cpio.gz').write_bytes(raw[start:start+meta['ramdisk']])
    (plan/'fstab.qcom').write_text(mount_plan(meta['entries']['fstab.qcom'][1].decode()),newline='\n')
    for name in ('init.emmc.true.rc','init.target.rc'):
        text=meta['entries'][name][1].decode()
        text=text.replace('/dev/block/bootdevice/by-name/system','/dev/block/loop0').replace('/dev/block/bootdevice/by-name/userdata','/dev/block/loop1').replace('/dev/block/bootdevice/by-name/cache','/dev/block/loop2')
        (plan/name).write_text(text,newline='\n')
    shutil.copyfile(system,storage/'system.img')
    if sha(storage/'system.img')!=SYSTEM_SHA:raise ValueError('Stock system copy failed')
    for name,size in [('data',512*1024*1024),('cache',64*1024*1024)]:
        path=storage/(name+'.img');p=linux(path)
        subprocess.run(['wsl','-d','kaios','--','/root/android81-leo/src/out/host/linux-x86/bin/make_ext4fs','-l',str(size),'-a',name,p],check=True,capture_output=True)
        repair=subprocess.run(['wsl','-d','kaios','--','e2fsck','-f','-y',p],capture_output=True)
        if repair.returncode not in (0,1):raise ValueError('New blank filesystem repair failed')
        subprocess.run(['wsl','-d','kaios','--','e2fsck','-f','-n',p],check=True,capture_output=True)
    (storage/'owner').write_text('LEO_KAIOS_SD_STORAGE_V1_695d\n',newline='\n')
    report={'serial':'695d','private':True,'storage_prepared':True,'bootable':False,
            'selector_implemented':False,'hardware_boot_validated':False,'internal_partitions_written':False,
            'data_source':'new empty filesystem; not copied from Android/KaiOS user data',
            'files':{p.name:{'bytes':p.stat().st_size,'sha256':sha(p)} for p in storage.iterdir() if p.is_file()},
            'remaining':['PID1 selector before recovery init','loop setup surviving stock init /dev recreation',
                         'audit all stock direct partition mounts','handle recovery boot mode and vold','hardware boot and driver tests']}
    (out/'manifest.json').write_text(json.dumps(report,indent=2)+'\n')
    (out/'README.txt').write_text('PRIVATE KaiOS SD storage preparation, Nokia 695d. NOT BOOTABLE YET.\nLeoKaiOS/system.img is the verified original KaiOS system, unchanged.\ndata.img (512 MiB) and cache.img (64 MiB) are freshly generated empty ext4 files.\nramdisk-plan is a proposed configuration, not an installed boot selector.\nDo not flash these files to internal partitions. Do not replace Android userdata.\nNo boot flag is created and the current Leo Recovery has no KaiOS SD boot action.\nA future PID1 recovery selector must mount the SD, attach loop devices, use the\nstock KaiOS init/ramdisk and prevent any fallback to internal system/data/cache.\nKernel kexec is disabled; a running recovery app cannot simply start KaiOS init.\n',encoding='utf-8')
    print(json.dumps({'output':str(out),'storage_prepared':True,'bootable':False,'phone_written':False},indent=2),flush=True)

if __name__=='__main__':main()
