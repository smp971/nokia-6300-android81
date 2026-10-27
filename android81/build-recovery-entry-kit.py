"""Offline private root-recovery entry kit and original KaiOS rollback ZIP.

Uses only pinned original local backups. Does not contact the phone.
"""
from pathlib import Path
import hashlib, importlib.util, json, shutil, zipfile

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('recovery_bundle',ROOT/'install-recovery-bundle.py')
rom=importlib.util.module_from_spec(spec);spec.loader.exec_module(rom)
RESCUE='2e4dee03305c5b617d5b0d51c064af350c817f3dc93f669ec302bdd97adf6fdc'

def sha(path):
    with path.open('rb') as source:return rom.digest_stream(source)

def main():
    dest=ROOT/'output/recovery-entry-kit';dest.mkdir(exist_ok=True)
    backups=dest/'stock-backups';backups.mkdir(exist_ok=True)
    sources={name:ROOT/'backup-live/kaios-before-internal'/name for name in ('boot-kaios.img','system-kaios.img')}
    sources['recovery-device.img']=ROOT/'backup-live/recovery-device.img'
    for name,source in sources.items():
        size,digest=rom.STOCK_BACKUPS[name]
        if source.stat().st_size!=size or sha(source)!=digest:raise ValueError('Original source mismatch: '+name)
        target=backups/name
        if not target.exists():shutil.copyfile(source,target)
    rom.verify_stock_backups(backups)
    rescue=ROOT/'output/recovery-debug.img'
    if rescue.stat().st_size!=33554432 or sha(rescue)!=RESCUE:raise ValueError('Unexpected root recovery')
    rootzip=dest/'leo-root-recovery-695d.zip'
    if rootzip.exists():raise FileExistsError(rootzip)
    with zipfile.ZipFile(rootzip,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        archive.write(rescue,'recovery-adbshell.img')
        archive.write(ROOT/'install-root-recovery.py','install-root-recovery.py')
        archive.writestr('README.txt','PRIVATE development Nokia 695d only.\nRoot ADB recovery image, matching the saved good recovery-adbshell copy.\nOffline: python install-root-recovery.py recovery-adbshell.img\nStock KaiOS root/ADB is a prerequisite; this does not unlock a stock phone.\nPreflight: add --preflight --serial 695d --adb PATH_TO_ADB\nWrite ONLY recovery: add --install --confirm-target 695d instead of --preflight.\nExisting recovery is saved and verified on PC first. No automatic reboot/restore.\nThen: adb -s 695d reboot recovery\nDo not run bootstrap on Leo: its stock boot/system guards will refuse.\nNeither clean installation nor rollback has been validated in this packaging step.\n')
        archive.writestr('manifest.json',json.dumps({'serial':'695d','image_sha256':RESCUE,'private':True,'hardware_flash_validated_this_build':False},indent=2))
    # The schema-1 installer also provides guarded boot/system rollback. It keeps
    # recovery available. Android user data must be wiped using KaiOS recovery.
    restore=dest/'kaios-original-restore-695d.zip'
    files={'images/boot.img':backups/'boot-kaios.img','images/system.img':backups/'system-kaios.img',
           'runtime/busybox':ROOT/'output/recovery-shell-candidate',
           'install-recovery-bundle.py':ROOT/'install-recovery-bundle.py'}
    rom.static_arm(files['runtime/busybox'].read_bytes())
    readme=dest/'README-restore.txt'
    readme.write_text('PRIVATE original KaiOS boot/system rollback for Nokia 695d only.\nUses the same root-recovery ADB installer and explicit flags as a schema-1 update.\nNo userdata or radio calibration backup is included. Personal data is NOT restored.\nFactory reset / wipe data from KaiOS recovery is required before normal stock boot.\nRecovery is retained; original recovery-device.img is kept separately in stock-backups.\nVerify: python install-recovery-bundle.py kaios-original-restore-695d.zip\nPreflight: add --preflight --serial 695d --adb PATH_TO_ADB\nFlash: use --install --serial 695d --confirm-target 695d --adb PATH_TO_ADB\nKeep root recovery until stock boot has been verified; no automatic reboot.\nRollback has not been executed to test this kit.\n',encoding='utf-8')
    files['README.txt']=readme
    meta={'format':'leo-private-recovery-bundle','schema':1,'serial':'695d','private':True,
          'version':'KaiOS-original','purpose':'stock-rollback','hardware_flash_validated':False,
          'requires_factory_reset_after_restore':True,
          'files':{name:{'bytes':path.stat().st_size,'sha256':sha(path)} for name,path in files.items()}}
    rom.validate_manifest(meta)
    with zipfile.ZipFile(restore,'x',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for name,path in sorted(files.items()):archive.write(path,name)
        archive.writestr('manifest.json',json.dumps(meta,indent=2))
    rom.verify(restore)
    with zipfile.ZipFile(rootzip) as archive:
        with archive.open('recovery-adbshell.img') as source:
            if rom.digest_stream(source)!=RESCUE:raise ValueError('Recovery archive verification failed')
    result={path.name:{'bytes':path.stat().st_size,'sha256':sha(path)} for path in (rootzip,restore)}
    (dest/'checksums.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2),flush=True)

if __name__=='__main__':main()
