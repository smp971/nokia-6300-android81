"""Read-only, serial-pinned boot/system/recovery snapshot plus updated system APKs.

No userdata, radio partitions, accounts or application files are copied.
Private images remain under ignored output/. Recovery is backup-only.
"""
from pathlib import Path
import datetime, hashlib, json, re, shlex, subprocess

ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
DEST=ROOT/'output/rom-snapshots'/datetime.datetime.now().strftime('%Y%m%d-%H%M%S')

def fw(*args,timeout=30):
    return subprocess.check_output(['python','-X','utf8',str(ROOT/'framework-command.py'),
        '--timeout',str(timeout),*args],timeout=timeout+5).decode().strip()

def sha(path):
    value=hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda:source.read(1048576),b''):value.update(chunk)
    return value.hexdigest()

def main():
    assert 'androidboot.serialno=695d' in fw('/system/bin/cat','/proc/cmdline')
    assert 'Validated internal Android partitions.' in fw('/system/bin/cat','/leo-framework-internal')
    mounts=fw('/system/bin/cat','/proc/mounts').splitlines()
    assert any(line.split()[1]=='/system' and 'ro' in line.split()[3].split(',') for line in mounts)
    assert fw('/system/bin/getprop','sys.boot_completed')=='1'
    assert set(re.findall(r'mCallState=(\d+)',fw('/system/bin/dumpsys','telephony.registry')))=={'0'}
    DEST.mkdir(parents=True,exist_ok=False)
    manifest={'format':'leo-private-snapshot','schema':1,'serial':'695d','read_only_source':True,
              'partitions':{},'overlays':[],'userdata_included':False,'radio_included':False}
    manifest['version']=re.search(r'versionName=([^\s]+)',fw('/system/bin/dumpsys','package','ro.leo.visual')).group(1)
    for name,number,size in [('boot',29,33554432),('system',30,838860800),('recovery',33,33554432)]:
        info=dict(line.split('=',1) for line in fw('/system/bin/cat',f'/sys/class/block/mmcblk0p{number}/uevent').splitlines() if '=' in line)
        assert info['PARTNAME']==name
        assert int(fw('/system/bin/cat',f'/sys/class/block/mmcblk0p{number}/size'))*512==size
        node=f'/dev/block/mmcblk0p{number}'
        expected=fw('/system/bin/sha256sum',node,timeout=180).split()[0]
        target=DEST/(name+'.img')
        print('Reading and verifying '+name+' ('+str(size//1048576)+' MiB)',flush=True)
        # Old recovery adbd has no working exec service. Its sync protocol can
        # read block nodes without PTY/newline conversion (as in the stock backup).
        outer='/dev/leo-rom-read-'+name
        def recovery(*command):
            return subprocess.check_output(ADB+['shell',shlex.join(['/system/bin/busybox',*command])],timeout=15).decode().strip()
        recovery('mknod',outer,'b',info['MAJOR'],info['MINOR'])
        assert recovery('stat','-c','%t:%T',outer)==f'{int(info["MAJOR"]):x}:{int(info["MINOR"]):x}'
        subprocess.run(ADB+['pull',outer,str(target.with_suffix('.partial'))],
                       capture_output=True,check=True,timeout=900)
        recovery('rm',outer)
        partial=target.with_suffix('.partial')
        assert partial.stat().st_size==size and sha(partial)==expected
        partial.rename(target)
        manifest['partitions'][name]={'file':target.name,'bytes':size,'sha256':expected,
                                     'node':node,'backup_only':name=='recovery'}
        (DEST/'snapshot.json').write_text(json.dumps(manifest,indent=2)+'\n')
    for line in fw('/system/bin/sh','/system/bin/pm','list','packages','-s','-f').splitlines():
        if not line.startswith('package:/data/app/'):continue
        active,package=line[8:].rsplit('=',1)
        assert re.fullmatch(r'[A-Za-z0-9_.]+',package)
        details=fw('/system/bin/dumpsys','package',package)
        assert 'Hidden system packages:' in details,package
        hidden=details.split('Hidden system packages:',1)[1]
        base=re.search(r'codePath=(/system/[^\r\n ]+)',hidden).group(1)
        candidates=fw('/system/bin/sh','-c','ls '+shlex.quote(base)+'/*.apk').splitlines() if not base.endswith('.apk') else [base]
        assert len(candidates)==1,package
        destination=candidates[0].removeprefix('/system')
        assert re.fullmatch(r'/(?:priv-app|app)/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.apk',destination)
        local=DEST/'overlays'/(package+'.apk');local.parent.mkdir(exist_ok=True)
        temporary='/tmp/leo-rom-snapshot.apk'
        expected=fw('/system/bin/sha256sum',active).split()[0]
        fw('/system/bin/cp',active,temporary)
        subprocess.run(ADB+['pull',temporary,str(local)],check=True,capture_output=True,timeout=90)
        assert sha(local)==expected
        fw('/system/bin/rm',temporary)
        manifest['overlays'].append({'package':package,'file':local.relative_to(DEST).as_posix(),
                                    'destination':destination,'sha256':expected})
        print('Saved active system update: '+package,flush=True)
    manifest['complete']=True
    (DEST/'snapshot.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('Verified private snapshot: '+str(DEST),flush=True)

if __name__=='__main__':main()
