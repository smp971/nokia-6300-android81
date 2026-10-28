"""Stage the two approved recovery ZIPs and stock backups on SD; never flash."""
from pathlib import Path
import argparse,gzip,hashlib,json,shlex,subprocess,uuid
ROOT=Path(__file__).resolve().parent
def sha(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda:f.read(1048576),b''):h.update(b)
    return h.hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--copy',action='store_true');p.add_argument('--confirm-target');p.add_argument('--adb',default='D:/ROMS/platform-tools/adb.exe');a=p.parse_args()
    manifest=json.loads((ROOT/'output/leo-recovery/manifest.json').read_text());catalog=manifest['catalog']
    for meta in catalog.values():
        path=Path(meta['path'])
        if path.stat().st_size!=meta['bytes'] or sha(path)!=meta['sha256']:raise ValueError('Approved ZIP changed')
    original=ROOT/'backup-live/recovery-device.img';recovery_sha='aa97beff2ca1c8f043e10ff077a43723f16332159b6503ce623f0fd5fb9c8cef'
    if original.stat().st_size!=33554432 or sha(original)!=recovery_sha:raise ValueError('Original recovery changed')
    if not a.copy:print('Approved files verified locally; no phone access');return
    if a.confirm_target!='695d':raise ValueError('Explicit target 695d required')
    adb=[a.adb,'-s','695d'];q=shlex.quote
    def shell(script,timeout=900):
        r=subprocess.run(adb+['shell',shlex.join(['/system/bin/sh','-c',script+'; r=$?; echo LEO_STAGE_RC_$r'])],capture_output=True,check=True,timeout=timeout)
        text=r.stdout.decode(errors='replace').replace('\r','').strip()
        if not text.endswith('LEO_STAGE_RC_0'):raise RuntimeError(text)
        return text[:-len('LEO_STAGE_RC_0')].strip()
    shell('test "$(/system/bin/busybox id -u)" = 0 && test "$(/system/bin/busybox cat /tmp/leo-debug-init)" = root-adb-v1 && ! /system/bin/busybox pidof system_server')
    mounts=shell('/system/bin/busybox cat /proc/self/mountinfo')
    if not any(len(f)>=6 and f[2:5]==['179:65','/','/sdcard'] and 'rw' in f[5].split(',') for f in map(str.split,mounts.splitlines())):raise ValueError('Mount external SD first')
    if int(shell('/system/bin/busybox df -k /sdcard').splitlines()[-1].split()[3])*1024<5*1024**3:raise ValueError('Need at least 5 GiB free for packages, originals and validation staging')
    base='/sdcard/LeoRecovery'
    shell('/system/bin/busybox mkdir -p '+base+'/packages '+base+'/stock-backups')
    def exists(path):return shell('if test -e '+q(path)+'; then echo yes; else echo no; fi')=='yes'
    def checked(path,digest):
        if shell('/system/bin/busybox sha256sum '+q(path)).split()[0]!=digest:raise ValueError('Existing/staged file mismatch: '+path)
    for meta in catalog.values():
        remote=base+'/packages/'+Path(meta['path']).name
        if exists(remote):checked(remote,meta['sha256']);continue
        incoming=remote+'.incoming-'+uuid.uuid4().hex
        print('Copying '+Path(meta['path']).name,flush=True)
        subprocess.run(adb+['push',meta['path'],incoming],check=True,timeout=900)
        checked(incoming,meta['sha256']);shell('/system/bin/busybox mv '+q(incoming)+' '+q(remote))
    stock=catalog['stock'];archive=base+'/packages/'+Path(stock['path']).name
    for part in stock['images']:
        remote=base+'/stock-backups/'+part['name']+'-kaios.img'
        if not exists(remote):
            print('Extracting stock '+part['name'],flush=True)
            shell('set -C; /system/bin/busybox unzip -p '+q(archive)+' '+q('images/'+part['name']+'.img')+' > '+q(remote))
        checked(remote,part['sha256'])
    remote=base+'/stock-backups/recovery-device.img'
    if not exists(remote):
        compressed=ROOT/'output/leo-recovery/original-recovery.img.gz'
        with original.open('rb') as src,gzip.open(compressed,'wb',compresslevel=1) as dest:
            for b in iter(lambda:src.read(1048576),b''):dest.write(b)
        incoming=remote+'.gz-'+uuid.uuid4().hex;subprocess.run(adb+['push',str(compressed),incoming],check=True,timeout=180)
        shell('set -C; /system/bin/busybox gzip -dc '+q(incoming)+' > '+q(remote));checked(remote,recovery_sha)
        shell('/system/bin/busybox rm '+q(incoming))
    checked(remote,recovery_sha);shell('/system/bin/busybox sync')
    (ROOT/'output/leo-recovery/sd-staging.json').write_text(json.dumps({'serial':'695d','approved_packages_verified':True,'stock_backups_verified':True,'partitions_written':False},indent=2)+'\n')
    print('Approved packages and original backups verified on SD. No partitions written.',flush=True)
if __name__=='__main__':main()
