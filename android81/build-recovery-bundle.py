"""Build an offline private boot/system recovery ZIP from a verified live snapshot.

Updated system APKs are overlaid into a COPY of the read-only system image.
Uses WSL debugfs/e2fsck; never connects to or writes the phone.
"""
from pathlib import Path
import argparse, hashlib, importlib.util, json, re, shutil, subprocess, zipfile

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('recovery_bundle',ROOT/'install-recovery-bundle.py')
bundle=importlib.util.module_from_spec(spec);spec.loader.exec_module(bundle)

def sha(path):
    with path.open('rb') as source:return bundle.digest_stream(source)

def linux(path):
    path=path.resolve().as_posix()
    if not re.match(r'^[A-Za-z]:/',path):raise ValueError('Use a local Windows workspace path')
    return '/mnt/'+path[0].lower()+path[2:]

def run(*args):
    result=subprocess.run(['wsl','-d','kaios','--',*args],capture_output=True,text=True,timeout=300)
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    return result.stdout

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('snapshot',type=Path)
    parser.add_argument('--fresh',action='store_true',help='Build an explicitly destructive clean-install candidate, with empty userdata')
    parser.add_argument('--output',type=Path,default=ROOT/'output/recovery-bundles')
    args=parser.parse_args();snapshot=args.snapshot.resolve()
    meta=json.loads((snapshot/'snapshot.json').read_text())
    assert meta['format']=='leo-private-snapshot' and meta.get('complete') and meta['serial']=='695d'
    assert meta['read_only_source'] and not meta['userdata_included'] and not meta['radio_included']
    version=json.loads((ROOT/'release-version.json').read_text())['version']
    assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[a-z0-9.-]+)?',version)
    assert meta['version']==version,'Snapshot and source versions differ'
    dest=args.output/('leo-'+('fresh' if args.fresh else 'recovery')+'-'+version+'-'+snapshot.name)
    dest.mkdir(parents=True,exist_ok=False)
    (dest/'images').mkdir();(dest/'runtime').mkdir()
    for part in ('boot','system','recovery'):
        entry=meta['partitions'][part];source=snapshot/entry['file']
        assert entry['file']==part+'.img' and entry['bytes']==(838860800 if part=='system' else 33554432)
        assert source.stat().st_size==entry['bytes'] and sha(source)==entry['sha256'],part
        if part!='recovery':shutil.copyfile(source,dest/'images'/(part+'.img'))
    system=dest/'images/system.img';system_linux=linux(system)
    label=dest/'system-file-context';label.write_bytes(b'u:object_r:system_file:s0\0')
    commands=[]
    assert len({item['destination'] for item in meta['overlays']})==len(meta['overlays'])
    for overlay in meta['overlays']:
        source=snapshot/overlay['file'];target=overlay['destination']
        assert re.fullmatch(r'/(?:app|priv-app)/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\.apk',target)
        assert source.resolve().is_relative_to(snapshot) and sha(source)==overlay['sha256']
        commands += ['rm '+target,'write "'+linux(source)+'" '+target,
                     'set_inode_field '+target+' mode 0100644','set_inode_field '+target+' uid 0',
                     'set_inode_field '+target+' gid 0',
                     'ea_set -f "'+linux(label)+'" '+target+' security.selinux']
    recipe=dest/'overlay.debugfs';recipe.write_text('\n'.join(commands)+'\n',encoding='utf-8',newline='\n')
    run('debugfs','-w','-f',linux(recipe),system_linux)
    check=dest/'overlay-readback.apk'
    for overlay in meta['overlays']:
        run('debugfs','-R','dump '+overlay['destination']+' "'+linux(check)+'"',system_linux)
        assert sha(check)==overlay['sha256'],'Overlay readback mismatch: '+overlay['package']
    clean=None
    if args.fresh:
        clean_spec=importlib.util.spec_from_file_location('clean_system',ROOT/'prepare-clean-system.py')
        clean_module=importlib.util.module_from_spec(clean_spec);clean_spec.loader.exec_module(clean_module)
        clean=clean_module.prepare(system,dest,linux,run)
        data=dest/'images/userdata.img'
        run('/root/android81-leo/src/out/host/linux-x86/bin/make_ext4fs','-l','2566893568','-a','data',linux(data))
        with data.open('r+b') as empty:empty.truncate(2566897152)
        # Android's legacy formatter leaves unset inode-bitmap padding. Repair
        # only this newly generated blank PC image, then require a clean check.
        repair=subprocess.run(['wsl','-d','kaios','--','e2fsck','-f','-y',linux(data)],capture_output=True,text=True,timeout=300)
        if repair.returncode not in (0,1):raise RuntimeError(repair.stdout+repair.stderr)
        run('e2fsck','-f','-n',linux(data))
        (dest/'clean-system-audit.json').write_text(json.dumps(clean,indent=2)+'\n')
    run('e2fsck','-f','-n',system_linux)
    helper=ROOT/'output/recovery-shell-candidate';bundle.static_arm(helper.read_bytes())
    shutil.copyfile(helper,dest/'runtime/busybox')
    shutil.copyfile(ROOT/'install-recovery-bundle.py',dest/'install-recovery-bundle.py')
    (dest/'README.txt').write_text('Private Leo '+version+' recovery snapshot for serial 695d.\n'
        'Contains proprietary device runtime: keep local/private. No userdata or radio image.\n'
        'This is an ADB recovery ZIP, not a stock Apply update signed package.\n'
        'Verify: python install-recovery-bundle.py PACKAGE.zip\n'
        'Recovery only: add --preflight --serial 695d --adb PATH_TO_ADB\n'
        'Explicit flash: add --install --serial 695d --confirm-target 695d --adb PATH_TO_ADB\n'
        'Only system and boot are written, in that order; recovery and data are retained.\n'
        'Keep the original snapshot and stock backups separately. Do not interrupt flashing.\n'
        'Flash/restore on hardware has not been validated by an offline build.\n',encoding='utf-8')
    allowed=bundle.ALLOWED | ({'images/userdata.img'} if args.fresh else set())
    if args.fresh:
        (dest/'README.txt').write_text('PRIVATE CLEAN-INSTALL CANDIDATE, Nokia 695d only.\n'
            'Erases ALL internal user data. Boot/system plus newly generated empty userdata.\n'
            'No accounts, messages, passwords, logs or calibration copied from userdata.\n'
            'Root ADB recovery required; this ZIP is NOT signed for stock Apply update.\n'
            'Offline verification: python install-recovery-bundle.py PACKAGE.zip\n'
            'Preflight: add --preflight --serial 695d --adb PATH_TO_ADB\n'
            'Installation additionally requires --install --confirm-target 695d --wipe-data --stock-backups DIRECTORY\n'
            'Keep verified original boot-kaios.img, system-kaios.img, recovery-device.img.\n'
            'System and blank userdata are written/verified first, boot LAST. Recovery/radio retained.\n'
            'Clean boot and hardware flash are NOT yet validated. Do not use on other devices.\n',encoding='utf-8')
    result={'format':'leo-private-recovery-bundle','schema':2 if args.fresh else 1,'version':version,'private':True,
            'serial':'695d','snapshot':snapshot.name,'source_commit':subprocess.check_output(
                ['git','-C',str(ROOT.parent),'rev-parse','HEAD']).decode().strip(),
            'working_tree_dirty':bool(subprocess.check_output(['git','-C',str(ROOT.parent),'status','--porcelain'])),
            'build_tool_sha256':{name:sha(ROOT/name) for name in ['snapshot-leo-rom.py','build-recovery-bundle.py','install-recovery-bundle.py']+(['prepare-clean-system.py','leo-first-setup.sh','leo-clean-audio-seed.sh'] if args.fresh else [])},
            'source_system_sha256':meta['partitions']['system']['sha256'],
            'recovery_backup_sha256':meta['partitions']['recovery']['sha256'],
            'overlays':meta['overlays'],'hardware_flash_validated':False,
            'files':{name:{'bytes':(dest/name).stat().st_size,'sha256':sha(dest/name)} for name in sorted(allowed)}}
    if clean:result['clean_install_audit']=clean
    bundle.validate_manifest(result)
    (dest/'manifest.json').write_text(json.dumps(result,indent=2)+'\n')
    archive=Path(str(dest)+'.zip')
    with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as output:
        for name in sorted(allowed|{'manifest.json'}):output.write(dest/name,name)
    bundle.verify(archive)
    print('Offline verified private recovery ZIP: '+str(archive),flush=True)
    print('ZIP SHA-256: '+sha(archive),flush=True)

if __name__=='__main__':main()
