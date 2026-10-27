"""Verify a private Leo recovery ZIP; flashing requires explicit recovery-only flags.

This is an ADB recovery installer, not an Android stock Apply update signature.
Only boot/system can be written. Userdata, recovery and radio are never targets.
"""
from pathlib import Path, PurePosixPath
import argparse, hashlib, json, re, shlex, shutil, struct, subprocess, sys, tempfile, uuid, zipfile

IMAGES={'boot':(29,33554432),'system':(30,838860800)}
ALLOWED={'images/boot.img','images/system.img','runtime/busybox',
         'install-recovery-bundle.py','README.txt'}

def digest_stream(source):
    value=hashlib.sha256()
    for chunk in iter(lambda:source.read(1048576),b''):value.update(chunk)
    return value.hexdigest()

def validate_manifest(meta):
    if meta.get('format')!='leo-private-recovery-bundle' or meta.get('schema')!=1:
        raise ValueError('Unsupported recovery bundle')
    if meta.get('serial')!='695d' or not meta.get('private'):
        raise ValueError('This snapshot is restricted to the development Nokia')
    if set(meta.get('files',{}))!=ALLOWED:
        raise ValueError('Only the documented boot/system payload and installer files are allowed')
    for name,entry in meta['files'].items():
        if PurePosixPath(name).is_absolute() or '..' in PurePosixPath(name).parts:
            raise ValueError('Unsafe archive path')
        if not isinstance(entry.get('bytes'),int) or not 0<entry['bytes']<=838860800:
            raise ValueError('Invalid payload size')
        if name not in ('images/boot.img','images/system.img') and entry['bytes']>4194304:
            raise ValueError('Oversized helper or installer')
        if not re.fullmatch(r'[0-9a-f]{64}',entry.get('sha256','')):
            raise ValueError('Invalid payload hash')
    for part,(_,size) in IMAGES.items():
        if meta['files']['images/'+part+'.img']['bytes']!=size:
            raise ValueError('Unexpected partition image size: '+part)
    return meta

def image_header(part,data,total):
    if part=='boot':
        if len(data)<64 or data[:8]!=b'ANDROID!':raise ValueError('Invalid Android boot header')
        kernel,ramdisk,second,page=(struct.unpack_from('<I',data,at)[0] for at in (8,16,24,36))
        if page not in (2048,4096,8192) or kernel==0 or ramdisk==0:
            raise ValueError('Unsupported boot geometry')
        if page+sum((size+page-1)//page*page for size in (kernel,ramdisk,second))>total:
            raise ValueError('Boot payload exceeds partition')
    else:
        if len(data)<2048 or data[1080:1082]!=b'\x53\xef':raise ValueError('Invalid ext4 system image')
        blocks=struct.unpack_from('<I',data,1028)[0];shift=struct.unpack_from('<I',data,1048)[0]
        if shift>6 or blocks==0 or blocks*(1024<<shift)>total:raise ValueError('Invalid ext4 geometry')

def verify(path):
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)) or set(names)!=ALLOWED|{'manifest.json'}:
            raise ValueError('Duplicate or unexpected ZIP entries')
        if archive.getinfo('manifest.json').file_size>1048576:
            raise ValueError('Manifest too large')
        meta=validate_manifest(json.loads(archive.read('manifest.json')))
        for name,entry in meta['files'].items():
            if archive.getinfo(name).file_size!=entry['bytes']:
                raise ValueError('Size mismatch: '+name)
            with archive.open(name) as source:
                if digest_stream(source)!=entry['sha256']:
                    raise ValueError('Hash mismatch: '+name)
        for part in IMAGES:
            with archive.open('images/'+part+'.img') as source:
                image_header(part,source.read(4096),meta['files']['images/'+part+'.img']['bytes'])
    return meta

def static_arm(data):
    if len(data)<52 or data[:6]!=b'\x7fELF\x01\x01' or struct.unpack_from('<H',data,18)[0]!=40:
        raise ValueError('Recovery helper must be ARM ELF32')
    offset=struct.unpack_from('<I',data,28)[0]
    size,count=struct.unpack_from('<HH',data,42)
    if size!=32 or not 0<count<=64 or offset+size*count>len(data):
        raise ValueError('Invalid ELF program headers')
    if any(struct.unpack_from('<I',data,offset+n*size)[0] in (2,3) for n in range(count)):
        raise ValueError('Recovery helper must be static, without an interpreter')

def recovery_preflight(adb,meta,helper):
    def shell(*args,allow_failure=False,timeout=30):
        # Legacy shell protocol reports no remote exit code. Carry it explicitly
        # instead of treating the local adb client's status as the command status.
        script=shlex.join(list(args))+'; result=$?; printf "\\nLEO_RC_%s\\n" "$result"'
        result=subprocess.run(adb+['shell',shlex.join(['/system/bin/sh','-c',script])],capture_output=True,timeout=timeout)
        text=result.stdout.decode(errors='replace').replace('\r','')
        status=re.search(r'\nLEO_RC_(\d+)\s*$',text)
        if not status:raise RuntimeError('Recovery command status not received')
        code=int(status.group(1));output=text[:status.start()].strip()
        if code and not allow_failure:raise RuntimeError(output or 'Recovery command failed')
        return code,output
    # The existing recovery shell is required to stage the independently static helper.
    _,identity=shell('/system/bin/sh','-c','id')
    if 'uid=0' not in identity:raise RuntimeError('Root recovery ADB is required')
    remote='/tmp/busybox-leo-rom'
    subprocess.run(adb+['push',str(helper),remote],check=True,capture_output=True,timeout=30)
    shell('/system/bin/busybox','chmod','700',remote)
    _,cmdline=shell(remote,'cat','/proc/cmdline')
    if 'androidboot.serialno='+meta['serial'] not in cmdline:
        raise RuntimeError('Wrong target device')
    code,server=shell(remote,'pidof','system_server',allow_failure=True)
    if code==0 or server:
        raise RuntimeError('Active Android detected. Reboot into the existing recovery first; nothing flashed.')
    code,recovery=shell(remote,'pidof','recovery',allow_failure=True)
    if code!=0 or not recovery:raise RuntimeError('A running recovery process is required')
    _,mounts=shell(remote,'cat','/proc/1/mountinfo')
    for line in mounts.splitlines():
        if line.split()[2]=='179:30':raise RuntimeError('System partition is mounted; nothing flashed')
    nodes={}
    for part,(number,size) in IMAGES.items():
        _,uevent=shell(remote,'cat',f'/sys/class/block/mmcblk0p{number}/uevent')
        info=dict(line.split('=',1) for line in uevent.splitlines() if '=' in line)
        _,sectors=shell(remote,'cat',f'/sys/class/block/mmcblk0p{number}/size')
        if info.get('PARTNAME')!=part or int(sectors)*512!=size or info.get('MAJOR')!='179' or int(info.get('MINOR',-1))!=number:
            raise RuntimeError('Partition identity/size mismatch: '+part)
        alias='/dev/leo-rom-target-'+part
        shell(remote,'mknod',alias,'b','179',str(number),allow_failure=True)
        _,node=shell(remote,'stat','-c','%t:%T',alias)
        if node!=f'{int(info["MAJOR"]):x}:{int(info["MINOR"]):x}':
            raise RuntimeError('Unexpected block device: '+part)
        nodes[part]=alias
    return shell,remote,nodes

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive',type=Path)
    mode=parser.add_mutually_exclusive_group()
    mode.add_argument('--preflight',action='store_true')
    mode.add_argument('--install',action='store_true')
    parser.add_argument('--serial')
    parser.add_argument('--confirm-target')
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--staging-parent',default='/sdcard',help='Existing mounted external SD directory')
    args=parser.parse_args()
    meta=verify(args.archive)
    print('Verified private Leo '+meta['version']+' recovery bundle (boot/system only)',flush=True)
    if not args.preflight and not args.install:return
    if args.serial!=meta['serial']:raise ValueError('Explicit matching --serial is required')
    if args.install and args.confirm_target!=meta['serial']:
        raise ValueError('Flashing also requires --confirm-target '+meta['serial'])
    adb=[args.adb,'-s',args.serial]
    with tempfile.TemporaryDirectory(prefix='leo-recovery-helper-') as temporary:
        with zipfile.ZipFile(args.archive) as archive:
            data=archive.read('runtime/busybox');static_arm(data)
            helper=Path(temporary)/'busybox';helper.write_bytes(data)
            shell,remote,nodes=recovery_preflight(adb,meta,helper)
            print('Recovery preflight passed; no partitions written',flush=True)
            if not args.install:return
            parent=args.staging_parent
            if not re.fullmatch(r'/[A-Za-z0-9_./-]+',parent) or '..' in PurePosixPath(parent).parts:
                raise ValueError('Invalid SD staging directory')
            _,resolved=shell(remote,'readlink','-f',parent)
            _,mounts=shell(remote,'cat','/proc/self/mountinfo')
            card=[line.split() for line in mounts.splitlines() if line.split()[2]=='179:65'
                  and (resolved==line.split()[4] or resolved.startswith(line.split()[4].rstrip('/')+'/'))]
            if not card:raise RuntimeError('Staging directory must be on the mounted external SD card (mmcblk1p1)')
            _,space=shell(remote,'df','-Pk',parent)
            available=int(space.splitlines()[-1].split()[3])*1024
            required=sum(size for _,size in IMAGES.values())+33554432
            if available<required or shutil.disk_usage(temporary).free<required:
                raise RuntimeError('At least 864 MiB free is required on both the SD card and PC temporary drive')
            staging=resolved.rstrip('/')+'/leo-rom-'+uuid.uuid4().hex
            shell(remote,'mkdir',staging)
            for part in ['system','boot']:
                local=Path(temporary)/(part+'.img')
                with archive.open('images/'+part+'.img') as source,local.open('xb') as output:
                    shutil.copyfileobj(source,output,1048576)
                subprocess.run(adb+['push',str(local),staging+'/'+part+'.img'],check=True,capture_output=True,timeout=900)
                _,actual=shell(remote,'sha256sum',staging+'/'+part+'.img',timeout=900)
                if actual.split()[0]!=meta['files']['images/'+part+'.img']['sha256']:
                    raise RuntimeError('SD staging verification failed: '+part)
            # Recheck that Android/system did not start while copying to SD.
            code,running=shell(remote,'pidof','system_server',allow_failure=True)
            _,mounted=shell(remote,'cat','/proc/1/mountinfo')
            if code==0 or running or any(line.split()[2]=='179:30' for line in mounted.splitlines()):
                raise RuntimeError('Android or mounted system detected after staging; nothing flashed')
            for part in ['system','boot']:
                node=nodes[part]
                print('Writing and verifying '+part,flush=True)
                command=shlex.join([remote,'dd','if='+staging+'/'+part+'.img','of='+node,'bs=1048576','conv=fsync'])+' 2>&1; result=$?; echo LEO_ROM_DD_EXIT_$result; exit "$result"'
                _,status=shell('/system/bin/sh','-c',command,timeout=900,allow_failure=True)
                if not status.rstrip().endswith('LEO_ROM_DD_EXIT_0'):raise RuntimeError('Recovery dd failed: '+part)
                shell(remote,'sync')
                _,actual=shell(remote,'sha256sum',node,timeout=180)
                if actual.split()[0]!=meta['files']['images/'+part+'.img']['sha256']:
                    raise RuntimeError('Written image verification failed: '+part)
            print('Boot/system verified. Recovery, radio and userdata retained. Reboot manually.')
            print('Verified staging files retained on SD: '+staging)

if __name__=='__main__':
    try:main()
    except (ValueError,RuntimeError,OSError,zipfile.BadZipFile,subprocess.SubprocessError) as error:
        print(str(error),file=sys.stderr);sys.exit(1)
