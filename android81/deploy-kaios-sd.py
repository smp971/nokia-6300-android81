"""Copy prepared KaiOS images to a new SD directory, never to internal blocks."""
from pathlib import Path
import argparse,gzip,hashlib,json,shlex,shutil,subprocess
ROOT=Path(__file__).resolve().parent
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
def external_sd_mounted(mountinfo):
    for line in mountinfo.splitlines():
        fields=line.split()
        if len(fields)>=6 and fields[2:5]==['179:65','/','/sdcard'] and 'rw' in fields[5].split(','):return True
    return False
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--install',action='store_true');p.add_argument('--confirm-target');p.add_argument('--adb',default='D:/ROMS/platform-tools/adb.exe');a=p.parse_args()
    source=ROOT/'output/kaios-sd-preparation';manifest=json.loads((source/'manifest.json').read_text())
    for name in ('system.img','data.img','cache.img','owner'):
        meta=manifest['files'][name];file=source/'LeoKaiOS'/name
        if file.stat().st_size!=meta['bytes'] or sha(file)!=meta['sha256']:raise ValueError('Local image mismatch: '+name)
    print('Prepared images verified on PC',flush=True)
    if not a.install:return
    if a.confirm_target!='695d':raise ValueError('Explicit target 695d required')
    adb=[a.adb,'-s','695d']
    def shell(script,timeout=600):
        r=subprocess.run(adb+['shell',shlex.join(['/system/bin/sh','-c',script+'; r=$?; echo LEO_SD_RC_$r'])],capture_output=True,check=True,timeout=timeout)
        text=r.stdout.decode(errors='replace').replace('\r','').strip()
        if not text.endswith('LEO_SD_RC_0'):raise RuntimeError(text)
        return text[:-len('LEO_SD_RC_0')].strip()
    shell('test "$(/system/bin/busybox id -u)" = 0 && test "$(/system/bin/busybox cat /tmp/leo-debug-init)" = root-adb-v1 && ! /system/bin/busybox pidof system_server')
    mounts=shell('/system/bin/busybox cat /proc/self/mountinfo')
    if not external_sd_mounted(mounts):raise ValueError('Mount real external SD at /sdcard first')
    free=shell('/system/bin/busybox df -k /sdcard').splitlines()[-1].split()
    if int(free[3])*1024<1800*1024*1024:raise ValueError('Need at least 1800 MiB free')
    # mkdir without -p refuses an existing KaiOS directory; no existing data replaced.
    shell('/system/bin/busybox mkdir /sdcard/LeoKaiOS')
    stage=ROOT/'output/kaios-sd-transfer';stage.mkdir(exist_ok=True)
    for name in ('system.img','data.img','cache.img'):
        file=source/'LeoKaiOS'/name;archive=stage/(name+'.gz')
        print('Compressing '+name,flush=True)
        with file.open('rb') as src,gzip.open(archive,'wb',compresslevel=1) as dest:shutil.copyfileobj(src,dest,1048576)
        remote='/sdcard/LeoKaiOS/'+name
        subprocess.run(adb+['push',str(archive),remote+'.gz'],check=True,timeout=600)
        print('Extracting and checking '+name,flush=True)
        shell('/system/bin/busybox gzip -dc '+remote+'.gz > '+remote)
        if shell('/system/bin/busybox sha256sum '+remote).split()[0]!=manifest['files'][name]['sha256']:raise ValueError('SD image mismatch: '+name)
        shell('/system/bin/busybox rm '+remote+'.gz')
    subprocess.run(adb+['push',str(source/'LeoKaiOS/owner'),'/sdcard/LeoKaiOS/owner'],check=True,timeout=60)
    shell('/system/bin/busybox sync')
    report={'serial':'695d','images_verified':True,'internal_partitions_written':False,'boot_requested':False,'files':manifest['files']}
    (stage/'deployment.json').write_text(json.dumps(report,indent=2)+'\n')
    print('KaiOS SD images deployed and verified. No boot request created.',flush=True)
if __name__=='__main__':main()
