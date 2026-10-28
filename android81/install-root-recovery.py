"""Verified stock KaiOS -> root ADB recovery entry for development Nokia 695d.

Default is local verification only. --preflight reads stock identity/geometry.
--install additionally backs up the existing recovery before replacing ONLY it.
No reboot, userdata formatting, boot/system/radio writes or automatic restore.
"""
from pathlib import Path
import argparse, hashlib, json, re, shlex, subprocess, time, uuid

RESCUE_SHA='2e4dee03305c5b617d5b0d51c064af350c817f3dc93f669ec302bdd97adf6fdc'
LEO_SHA='e9407739db7d7f17a57a1da19e67612d1047c34c53fc9bf97631da7cfce9ff5e'
SIZE=33554432
STOCK={'boot':(29,'a23fcbcc993a0f5f1f03736eb59d7e431604d9c2d8900f100b927c06a1c81338'),
       'system':(30,'a16d027dba2702fc9114ef3269770c30595a8e9b595329e5b7527588345fbdd6')}

def digest(path):
    sha=hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda:source.read(1048576),b''):sha.update(chunk)
    return sha.hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image',type=Path)
    source=parser.add_mutually_exclusive_group()
    source.add_argument('--leo',action='store_true',help='Explicit Leo Recovery 0.1 prototype; requires internal Leo Android instead of stock KaiOS')
    source.add_argument('--from-recovery',action='store_true',help='Explicit recovery UI update from the known root recovery, with Android absent')
    mode=parser.add_mutually_exclusive_group()
    mode.add_argument('--preflight',action='store_true')
    mode.add_argument('--install',action='store_true')
    parser.add_argument('--serial');parser.add_argument('--confirm-target')
    parser.add_argument('--adb',default='adb')
    parser.add_argument('--backup-dir',type=Path,default=Path('android81/backup-live/root-recovery-entry'))
    args=parser.parse_args()
    approved=LEO_SHA if args.leo or args.from_recovery else RESCUE_SHA
    if args.image.stat().st_size!=SIZE or digest(args.image)!=approved:
        raise ValueError('Unexpected root recovery image')
    print('Verified known Nokia root recovery image',flush=True)
    if not(args.preflight or args.install):return
    if args.serial!='695d' or (args.install and args.confirm_target!='695d'):
        raise ValueError('Explicit serial/confirmation for 695d required')
    adb=[args.adb,'-s','695d'];bb='/system/bin/busybox'
    def shell(*command,timeout=60):
        script=shlex.join(list(command))+'; rc=$?; printf "\\nLEO_RESCUE_RC_%s\\n" "$rc"'
        readonly=len(command)>1 and command[0]==bb and command[1] in ('id','cat','stat','sha256sum')
        for attempt in range(3 if readonly else 1):
            result=subprocess.run(adb+['shell',shlex.join(['/system/bin/sh','-c',script])],capture_output=True,timeout=timeout)
            if result.returncode!=255 or not readonly or attempt==2:break
            time.sleep(0.3)
        result.check_returncode()
        text=result.stdout.decode(errors='replace').replace('\r','')
        match=re.search(r'\nLEO_RESCUE_RC_(\d+)\s*$',text)
        if not match or match[1]!='0':raise RuntimeError('Stock ADB command failed: '+text)
        return text[:match.start()].strip()
    if not re.search(r'\buid=0\b',shell(bb,'id')):raise RuntimeError('Already rooted stock KaiOS ADB required')
    if 'androidboot.serialno=695d' not in shell(bb,'cat','/proc/cmdline'):
        raise RuntimeError('Wrong phone')
    nodes={}
    for part,number,size,major,minor in [('boot',29,SIZE,179,29),('system',30,838860800,179,30),('recovery',33,SIZE,259,1)]:
        info=dict(line.split('=',1) for line in shell(bb,'cat',f'/sys/class/block/mmcblk0p{number}/uevent').splitlines() if '=' in line)
        if info.get('PARTNAME')!=part or info.get('MAJOR')!=str(major) or info.get('MINOR')!=str(minor):
            raise RuntimeError('Unexpected partition: '+part)
        if int(shell(bb,'cat',f'/sys/class/block/mmcblk0p{number}/size'))*512!=size:
            raise RuntimeError('Unexpected partition size: '+part)
        node='/dev/leo-rescue-'+part
        shell('/system/bin/sh','-c','test -b '+node+' || '+shlex.join([bb,'mknod',node,'b',str(major),str(minor)]))
        if shell(bb,'stat','-c','%t:%T',node)!=f'{major:x}:{minor:x}':raise RuntimeError('Wrong block alias')
        nodes[part]=node
    if args.leo:
        if shell(bb,'cat','/leo-framework-internal')!='Validated internal Android partitions.':
            raise RuntimeError('Leo prototype installation requires the known internal Android boot')
    elif args.from_recovery:
        if shell(bb,'cat','/tmp/leo-debug-init')!='root-adb-v1':
            raise RuntimeError('Known root recovery marker required')
        if shell('/system/bin/sh','-c','if /system/bin/busybox pidof system_server >/dev/null; then exit 1; fi; /system/bin/busybox cat /proc/1/comm')!='init':
            raise RuntimeError('Recovery init without Android required')
    else:
        for part,(_,expected) in STOCK.items():
            if shell(bb,'sha256sum',nodes[part],timeout=300).split()[0]!=expected:
                raise RuntimeError('Stock boot/system required; this bootstrap must NOT run on Leo')
    print('Identity/geometry and selected source mode verified; no partition written',flush=True)
    if not args.install:return
    args.backup_dir.mkdir(parents=True,exist_ok=True)
    before=shell(bb,'sha256sum',nodes['recovery'],timeout=120).split()[0]
    backup=args.backup_dir/('recovery-before-'+before+'.img')
    if not backup.exists():
        incoming=backup.with_suffix('.incoming')
        if incoming.exists():raise RuntimeError('Preserve/review previous incomplete recovery backup')
        subprocess.run(adb+['pull',nodes['recovery'],str(incoming)],check=True,timeout=180)
        if incoming.stat().st_size!=SIZE or digest(incoming)!=before:raise RuntimeError('Recovery backup mismatch')
        incoming.rename(backup)
    if backup.stat().st_size!=SIZE or digest(backup)!=before:raise RuntimeError('Existing recovery backup mismatch')
    # Early adbd has a private mount namespace: Android's /data is not mounted
    # there. Its /tmp is a verified writable tmpfs; stock KaiOS keeps its path.
    remote=('/tmp/' if args.leo or args.from_recovery else '/data/local/tmp/')+'leo-rescue-'+uuid.uuid4().hex
    shell(bb,'mkdir','-m','700',remote)
    subprocess.run(adb+['push',str(args.image),remote+'/recovery.img'],check=True,timeout=180)
    if shell(bb,'sha256sum',remote+'/recovery.img').split()[0]!=approved:raise RuntimeError('Staging hash mismatch')
    if shell(bb,'sha256sum',nodes['recovery']).split()[0]!=before:raise RuntimeError('Recovery changed during preparation')
    shell(bb,'dd','if='+remote+'/recovery.img','of='+nodes['recovery'],'bs=1048576',timeout=180)
    shell(bb,'sync')
    if shell(bb,'sha256sum',nodes['recovery']).split()[0]!=approved:raise RuntimeError('Recovery write verification failed')
    (args.backup_dir/'entry-result.json').write_text(json.dumps({'serial':'695d','before':before,'after':approved,'backup':str(backup),'only_partition_written':'recovery'},indent=2))
    print('Root recovery written and verified. Original saved on PC. Reboot manually with adb -s 695d reboot recovery.')

if __name__=='__main__':main()
