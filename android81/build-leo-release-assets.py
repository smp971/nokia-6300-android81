"""Assemble private Leo release ZIPs from verified firmware and test results."""
import argparse
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

import recovery_image


ROOT = Path(__file__).resolve().parent
UMTP_COMMIT = '8f20c6f8d47d1057c9793aa23bd0a678d54084e3'
OWNER = {
    'LeoAndroid': b'LEO_ANDROID_SD_STORAGE_V1_695d\n',
    'LeoKaiOS': b'LEO_KAIOS_SD_STORAGE_V1_695d\n',
}


def load_script(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def sha(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def copy_verified(source, archive, name, expected):
    digest = hashlib.sha256()
    size = 0
    with archive.open(name, 'w') as member:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            size += len(chunk)
            if size > expected['bytes']:
                raise ValueError(f'{name} exceeds the approved size')
            digest.update(chunk)
            member.write(chunk)
    if size != expected['bytes'] or digest.hexdigest() != expected['sha256']:
        raise ValueError(f'{name} failed the source verification')


def storage_zip(output, folder, rom, system, templates, assets):
    files = {f'{folder}/system.img': system}
    with zipfile.ZipFile(rom) as source, zipfile.ZipFile(output, 'x', zipfile.ZIP_DEFLATED, compresslevel=3) as target:
        with source.open('images/system.img') as image:
            copy_verified(image, target, f'{folder}/system.img', system)
        for name in ('data', 'cache'):
            member = f'{folder}/{name}.img'
            files[member] = templates[name]
            with gzip.open(assets / f'{name}.img.gz', 'rb') as image:
                copy_verified(image, target, member, templates[name])
        target.writestr(f'{folder}/owner', OWNER[folder])
        target.writestr('storage-manifest.json', json.dumps({'serial': '695d', 'files': files}, indent=2) + '\n')
        target.write(ROOT / 'INSTALL-GUIDE.md', 'INSTALL-GUIDE.md')
    with zipfile.ZipFile(output) as target:
        if target.testzip() is not None:
            raise ValueError(f'{output.name} failed archive readback')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('output', 'stock-kit', 'qfil', 'leo-rom', 'recovery-dir', 'sd-assets', 'umtpsrc'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    bundle = load_script('bundle', 'install-recovery-bundle.py')
    installer = load_script('root_installer', 'install-root-recovery.py')
    args.output.mkdir(parents=True, exist_ok=True)
    image = args.recovery_dir / 'leo-recovery-0.2.img'
    report = json.loads((args.recovery_dir / 'manifest.json').read_text(encoding='utf-8'))
    evidence = json.loads((args.recovery_dir / 'hardware-radio-result.json').read_text(encoding='utf-8'))
    report['installer_approved'] = sha(image) == report['sha256'] == installer.LEO_SD_RADIO_SHA
    if not report['installer_approved'] or image.stat().st_size != 33554432:
        raise ValueError('Unexpected recovery image')
    if (not evidence['boot_completed'] or evidence['radio'] != 'started' or
        evidence['ril_1'] != 'running' or evidence['ril_2'] != 'running' or
        evidence['startup_script_sha256'] != report['leo_sd']['radio_start_sha256'] or
        '179:25 / /firmware ro,' not in evidence['firmware_mount'] or not evidence['baseband']):
        raise ValueError('SD radio evidence does not match the packaged configuration')
    if sha(args.leo_rom) != report['catalog']['fresh']['sha256']:
        raise ValueError('Leo ZIP differs from the recovery catalog')
    files = recovery_image.unpack(image.read_bytes())['entries']
    if hashlib.sha256(files['leo-android-root/sbin/leo-sd-radio-start.sh'][1]).hexdigest() != evidence['startup_script_sha256']:
        raise ValueError('Embedded SD radio script mismatch')
    report['phone_written'] = True
    report['hardware_boot_validated'] = True
    report['hardware_sd_radio'] = evidence
    with zipfile.ZipFile(args.output / 'leo-recovery-sd-radio-20261116.zip', 'x', zipfile.ZIP_DEFLATED, compresslevel=4) as archive:
        archive.write(image, image.name)
        archive.writestr('manifest.json', json.dumps(report, indent=2) + '\n')
        for name in ('install-root-recovery.py', 'install-recovery-bundle.py', 'INSTALL-GUIDE.md', 'LEO-RECOVERY.md', 'QFIL-RECOVERY.md'):
            archive.write(ROOT / name, name)
        archive.write(args.umtpsrc / 'LICENSE', 'third-party/uMTP-Responder-LICENSE')
        archive.writestr('third-party/SOURCE.txt', f'uMTP-Responder {UMTP_COMMIT}\n'
                         'Corresponding responder source: umtp-responder-source-8f20c6f.tar.gz\n'
                         "Build on Debian with make CC='arm-linux-gnueabihf-gcc -static -D_FILE_OFFSET_BITS=64'\n")
    leo = bundle.verify(args.leo_rom)
    stock_path = args.stock_kit / 'kaios-original-restore-695d.zip'
    stock = bundle.verify(stock_path)
    if leo['schema'] != 2 or stock['schema'] != 1 or sha(stock_path) != report['catalog']['stock']['sha256']:
        raise ValueError('Unexpected ROM pair')
    templates = report['leo_sd']['templates']
    storage_zip(args.output / 'leo-sd-storage-20261116.zip', 'LeoAndroid', args.leo_rom,
                leo['files']['images/system.img'], templates, args.sd_assets)
    print('Leo SD storage verified.', flush=True)
    storage_zip(args.output / 'kaios-sd-storage-695d.zip', 'LeoKaiOS', stock_path,
                stock['files']['images/system.img'], templates, args.sd_assets)
    print('KaiOS SD storage verified.', flush=True)
    original = args.stock_kit / 'stock-backups'
    bundle.verify_stock_backups(original)
    with zipfile.ZipFile(args.output / 'leo-stock-originals-695d.zip', 'x', zipfile.ZIP_DEFLATED, compresslevel=3) as archive:
        for name in bundle.STOCK_BACKUPS:
            archive.write(original / name, 'stock-backups/' + name)
    entry = args.stock_kit / 'leo-root-recovery-695d.zip'
    with zipfile.ZipFile(entry) as archive:
        if hashlib.sha256(archive.read('recovery-adbshell.img')).hexdigest() != installer.RESCUE_SHA:
            raise ValueError('Root entry image mismatch')
    for path in (args.leo_rom, stock_path, entry, args.qfil):
        shutil.copyfile(path, args.output / path.name)
    commit = subprocess.check_output(['git', '-C', str(args.umtpsrc), 'rev-parse', 'HEAD'], text=True).strip()
    if commit != UMTP_COMMIT:
        raise ValueError('Unexpected uMTP Responder source commit')
    subprocess.run(['git', '-C', str(args.umtpsrc), 'archive', '--format=tar.gz',
                    '--prefix=uMTP-Responder/', '--output=' + str((args.output / 'umtp-responder-source-8f20c6f.tar.gz').resolve()),
                    commit], check=True)
    shutil.copyfile(ROOT / 'INSTALL-GUIDE.md', args.output / 'INSTALL-GUIDE.md')
    print('Private release storage, recovery and originals assembled.', flush=True)


if __name__ == '__main__':
    main()
