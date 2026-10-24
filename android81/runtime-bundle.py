"""Capture/verify a local Leo runtime update; no boot images or user data.

Capture reads the already validated phone, so it is a deployment snapshot, not
a claim that proprietary inputs can be rebuilt from this Git repository alone.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shlex
import subprocess
import sys
import tarfile
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parent
PRIVATE=tuple('/system/vendor/leo-'+n+'/' for n in ('radio','audio','camera','ims','gps'))
EXACT={'/system/etc/gps.conf','/system/etc/permissions/android.hardware.location.gps.xml',
       '/system/vendor/lib/hw/audio.primary.default.so','/system/etc/audio_policy.conf',
       '/system/etc/audio_policy_configuration.xml','/system/etc/mixer_paths.xml',
       '/system/etc/audio_platform_info.xml','/system/priv-app/LeoIms/LeoIms.apk'}

def allowed(target):
    if not isinstance(target,str) or not re.fullmatch(r'/[A-Za-z0-9_./@+-]+',target):return False
    if str(PurePosixPath(target))!=target or '..' in PurePosixPath(target).parts:return False
    return target.startswith(PRIVATE) or target in EXACT or bool(re.fullmatch(r'/system/etc/init/(?:init\.)?leo[-.][A-Za-z0-9_.-]+\.rc',target))

def digest(data):return hashlib.sha256(data).hexdigest()

def verify(archive):
    with zipfile.ZipFile(archive) as z:
        names=z.namelist()
        if len(names)>1500 or sum(i.file_size for i in z.infolist())>768*1024*1024:raise ValueError('Archive exceeds runtime bounds')
        if len(names)!=len(set(names)):raise ValueError('Duplicate archive member')
        m=json.loads(z.read('manifest.json'))
        if m.get('format')!='leo-runtime-update' or m.get('schema')!=1:raise ValueError('Unknown format')
        expected={'manifest.json','runtime-bundle.py'}
        if digest(z.read('runtime-bundle.py'))!=m['installer_sha256']:raise ValueError('Installer mismatch')
        targets=set()
        for name,row in m['files'].items():
            if not re.fullmatch(r'payload/[0-9]{4}',name):raise ValueError('Invalid payload path')
            target=row['target']
            if row['kind']=='app':
                if target not in ('ro.leo.visual','ro.leo.keypad'):raise ValueError('Unexpected app')
            elif row['kind']!='system' or not allowed(target):raise ValueError('Disallowed system path')
            if target in targets:raise ValueError('Duplicate target')
            targets.add(target)
            if row['mode'] not in (0o644,0o755):raise ValueError('Invalid file mode')
            raw=z.read(name)
            if len(raw)!=row['bytes'] or digest(raw)!=row['sha256']:raise ValueError('Payload mismatch: '+name)
            expected.add(name)
        if set(names)!=expected:raise ValueError('Unexpected archive members')
        if not {'ro.leo.visual','ro.leo.keypad'}.issubset(targets):raise ValueError('Missing apps')
        return m

class Phone:
    def __init__(self,adb,serial):self.adb=[adb,'-s',serial]
    def run(self,*a):return subprocess.check_output(self.adb+list(a),timeout=120).decode(errors='replace').replace('\r','').strip()
    def shell(self,*a):return self.run('shell',shlex.join(['/sbin/leo-framework-exec',*a]))
    def script(self,s):return self.shell('/system/bin/sh','-c',s)
    def framework(self,*a):
        env=[]
        for line in self.shell('/system/bin/cat','/init.environ.rc').splitlines():
            f=line.split()
            if len(f)==3 and f[0]=='export':env.append(f[1]+'='+f[2])
        if not any(s.startswith('BOOTCLASSPATH=') for s in env):raise RuntimeError('Missing framework environment')
        return self.shell('/system/bin/env',*env,*a)
    def baseline(self):
        if self.shell('/system/bin/getprop','ro.build.version.sdk')!='27':raise ValueError('Requires API 27')
        if self.shell('/system/bin/cat','/leo-framework-internal')!='Validated internal Android partitions.':raise ValueError('Existing internal Leo port required')
        if self.shell('/system/bin/uname','-r')!='4.9.240-perf-g4f9e270fd77f':raise ValueError('Kernel mismatch')
    def read(self,target):
        self.shell('/system/bin/cp',target,'/tmp/leo-bundle-read')
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'file';self.run('pull','/tmp/leo-bundle-read',str(p));return p.read_bytes()

def capture(phone,destination):
    phone.baseline();rows={};raws={}
    targets=[]
    for prefix in PRIVATE:
        targets+=phone.shell('/system/bin/find',prefix.rstrip('/'),'-type','f').splitlines()
    targets+=phone.script('find /system/etc/init -maxdepth 1 -type f -name "*leo*"').splitlines()
    for target in sorted(EXACT):
        if phone.script('test -f '+shlex.quote(target)+' && echo yes || true')=='yes':targets.append(target)
    targets=sorted(set(targets))
    if not all(allowed(t) for t in targets):raise ValueError('Capture outside runtime scope')
    # One archive transfer keeps the audit bounded even with hundreds of camera libraries.
    phone.script('cd / && tar -cf /tmp/leo-runtime-capture.tar '+shlex.join([t.lstrip('/') for t in targets]))
    with tempfile.TemporaryDirectory() as tmp:
        local=Path(tmp)/'capture.tar';phone.run('pull','/tmp/leo-runtime-capture.tar',str(local))
        with tarfile.open(local) as tar:
            members=tar.getmembers()
            if {m.name for m in members}!={t.lstrip('/') for t in targets} or len(members)!=len(targets):raise ValueError('Unexpected capture member')
            for member in members:
                target='/'+member.name;mode=member.mode&0o7777
                if not member.isfile() or mode not in (0o644,0o755):raise ValueError('Symlink/type/mode refused: '+target)
                raw=tar.extractfile(member).read();name='payload/%04d'%len(rows)
                rows[name]=dict(target=target,kind='system',mode=mode,bytes=len(raw),sha256=digest(raw));raws[name]=raw
    for package,name in [('ro.leo.keypad','leo-keypad-ime.apk'),('ro.leo.visual','leo-visual.apk')]:
        raw=(ROOT/'output'/name).read_bytes();key='payload/%04d'%len(rows)
        rows[key]=dict(target=package,kind='app',mode=0o644,bytes=len(raw),sha256=digest(raw));raws[key]=raw
    version=json.loads((ROOT/'release-version.json').read_text())
    sources={}
    for name in subprocess.check_output(['git','-C',str(ROOT.parent),'ls-files','-z','android81']).decode().split('\0'):
        if name and (ROOT.parent/name).is_file():sources[name]=digest((ROOT.parent/name).read_bytes())
    installer=Path(__file__).read_bytes()
    m=dict(format='leo-runtime-update',schema=1,**version,files=rows,installer_sha256=digest(installer),
           source_commit=subprocess.check_output(['git','-C',str(ROOT.parent),'rev-parse','HEAD']).decode().strip(),
           source_sha256=sources,working_tree_dirty=bool(subprocess.check_output(['git','-C',str(ROOT.parent),'status','--porcelain'])),
           provenance='Validated-device snapshot plus locally rebuilt Java apps; proprietary runtime inputs required.',
           kernel='4.9.240-perf-g4f9e270fd77f',requires='Existing internal Leo Android port, root namespace wrapper, matching development signers')
    destination.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(destination,'x',zipfile.ZIP_DEFLATED) as z:
        z.writestr('manifest.json',json.dumps(m,indent=2,ensure_ascii=False)+'\n');z.writestr('runtime-bundle.py',installer)
        for name,raw in raws.items():z.writestr(name,raw)
    verify(destination);print('Created',len(rows),'verified payloads:',destination)

def compare(phone,archive,m):
    phone.baseline();same=0;system={}
    for row in m['files'].values():
        target=row['target']
        if row['kind']=='app':
            line=phone.framework('/system/bin/sh','/system/bin/pm','path',target)
            if not line.startswith('package:/') or '\n' in line:raise ValueError('Single existing APK required')
            target=line[len('package:'):]
        else:system[target]=row['sha256'];continue
        if phone.shell('/system/bin/sha256sum',target).split()[0]!=row['sha256']:raise ValueError('Device differs: '+row['target'])
        same+=1
    for line in phone.shell('/system/bin/sha256sum',*system).splitlines():
        actual,target=line.split(None,1)
        if system.pop(target,None)!=actual:raise ValueError('Device differs: '+target)
        same+=1
    if system:raise ValueError('Missing device hash output')
    print('Device matches',same,'payload hashes; no writes performed')

def install(phone,archive,m,backup):
    phone.baseline()
    calls=re.findall(r'^\s*mCallState=(\d+)',phone.shell('/system/bin/dumpsys','telephony.registry'),re.M)
    if calls!=['0','0']:raise ValueError('Both calls must be idle')
    # Rollback is a host-side copy, independent of /data. Keep it until a full boot passes.
    backup.mkdir(parents=True,exist_ok=False);saved={}
    for name,row in m['files'].items():
        target=row['target'];path=target
        if row['kind']=='app':
            line=phone.framework('/system/bin/sh','/system/bin/pm','path',target)
            if not line.startswith('package:/') or '\n' in line:raise ValueError('Existing single APK required')
            path=line[len('package:'):]
        exists=phone.script('test -f '+shlex.quote(path)+' && echo yes || true')=='yes'
        old=dict(row,exists=exists)
        if exists:
            raw=phone.read(path);old['sha256']=digest(raw);old['bytes']=len(raw)
            old['mode']=int(phone.shell('/system/bin/stat','-c','%a',path),8)
            (backup/name.replace('/','-')).write_bytes(raw)
        saved[name]=old
    (backup/'rollback.json').write_text(json.dumps(saved,indent=2))
    write_payloads(phone,archive,m['files'])
    print('Installed. Reboot manually when ready. Rollback copy:',backup)

def write_payloads(phone,archive,rows,rollback=False):
    # Stage and verify every byte before changing /system. Extract locally only known names.
    with tempfile.TemporaryDirectory() as tmp:
        for name,row in rows.items():
            if rollback and not row['exists']:continue
            raw=(Path(archive)/name.replace('/','-')).read_bytes() if rollback else zipfile.ZipFile(archive).read(name)
            if len(raw)!=row['bytes'] or digest(raw)!=row['sha256']:raise ValueError('Invalid input')
            source=Path(tmp)/name.replace('/','-');source.write_bytes(raw)
            phone.run('push',str(source),'/tmp/leo-update-'+name.split('/')[-1])
            if phone.shell('/system/bin/sha256sum','/tmp/leo-update-'+name.split('/')[-1]).split()[0]!=row['sha256']:raise ValueError('Transfer mismatch')
    commands=['set -eu','mount -o remount,rw /system',"trap 'mount -o remount,ro /system' EXIT"]
    for name,row in rows.items():
        if row['kind']!='system':continue
        target=row['target']
        if not allowed(target):raise ValueError('Disallowed rollback target')
        if rollback and not row['exists']:commands.append('rm -f '+shlex.quote(target));continue
        source='/tmp/leo-update-'+name.split('/')[-1];temp=target+'.leo-update'
        commands+=['mkdir -p '+shlex.quote(str(PurePosixPath(target).parent)),
                   'cp '+shlex.quote(source)+' '+shlex.quote(temp),'chmod '+format(row['mode'],'o')+' '+shlex.quote(temp),
                   'chown 0:0 '+shlex.quote(temp),'mv -f '+shlex.quote(temp)+' '+shlex.quote(target),'restorecon '+shlex.quote(target)]
    commands.append('sync');phone.script('\n'.join(commands))
    expected={row['target']:row['sha256'] for row in rows.values() if row['kind']=='system' and (not rollback or row['exists'])}
    for line in phone.shell('/system/bin/sha256sum',*expected).splitlines():
        actual,target=line.split(None,1)
        if expected.pop(target,None)!=actual:raise RuntimeError('Installed hash mismatch: '+target)
    if expected:raise RuntimeError('Missing installed hash output')
    for name,row in rows.items():
        if row['kind']=='app':
            output=phone.framework('/system/bin/sh','/system/bin/pm','install','-r',*(['-d'] if rollback else []),'/tmp/leo-update-'+name.split('/')[-1])
            if 'Success' not in output:raise RuntimeError('APK installation failed; use saved rollback')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('archive',type=Path)
    mode=p.add_mutually_exclusive_group();mode.add_argument('--capture',action='store_true');mode.add_argument('--check-device',action='store_true');mode.add_argument('--install',action='store_true');mode.add_argument('--rollback',type=Path)
    p.add_argument('--backup',type=Path);p.add_argument('--adb',default='adb');p.add_argument('--serial');a=p.parse_args()
    online=a.capture or a.check_device or a.install or a.rollback
    if online and not a.serial:p.error('Explicit --serial required')
    if a.install and not a.backup:p.error('--backup must name a new host directory')
    phone=Phone(a.adb,a.serial) if online else None
    if a.capture:capture(phone,a.archive);return
    m=verify(a.archive);print('Verified Leo',m['version'],len(m['files']),'files')
    if a.check_device:compare(phone,a.archive,m)
    if a.install:install(phone,a.archive,m,a.backup)
    if a.rollback:
        phone.baseline();rows=json.loads((a.rollback/'rollback.json').read_text())
        if set(rows)!=set(m['files']):raise ValueError('Rollback belongs to a different update')
        for name,row in rows.items():
            if (row['kind'],row['target'])!=(m['files'][name]['kind'],m['files'][name]['target']):raise ValueError('Rollback target mismatch')
            if row['mode'] not in (0o644,0o755) or not isinstance(row['exists'],bool):raise ValueError('Invalid rollback metadata')
        calls=re.findall(r'^\s*mCallState=(\d+)',phone.shell('/system/bin/dumpsys','telephony.registry'),re.M)
        if calls!=['0','0']:raise ValueError('Both calls must be idle')
        write_payloads(phone,a.rollback,rows,True);print('Rollback restored; reboot manually')

if __name__=='__main__':main()
