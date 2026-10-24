"""Preflight, rebuild and package the two Leo Java apps. No ADB or partition writes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
ANDROID = '{http://schemas.android.com/apk/res/android}'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def version():
    value = json.loads((ROOT / 'release-version.json').read_text())
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[a-z0-9.-]+)?', value['version']):
        raise ValueError('Invalid release version')
    for manifest in ['visual-shell/AndroidManifest.xml', 'keypad-ime/AndroidManifest.xml']:
        app = ET.parse(ROOT / manifest).getroot()
        if app.get(ANDROID + 'versionName') != value['version'] or int(app.get(ANDROID + 'versionCode')) != value['version_code']:
            raise ValueError('Version mismatch: ' + manifest)
    return value


def preflight(aosp, java):
    required = [aosp / s for s in [
        'prebuilts/sdk/27/android.jar', 'prebuilts/sdk/tools/lib/dx.jar',
        'out/host/linux-x86/bin/aapt', 'out/host/linux-x86/bin/zipalign',
        'out/host/linux-x86/framework/signapk.jar',
        'build/target/product/security/testkey.x509.pem',
        'build/target/product/security/testkey.pk8',
        'out/target/common/obj/JAVA_LIBRARIES/com.android.vcard_intermediates/classes.jar']]
    required += [java / 'bin' / name for name in ['java', 'javac', 'jar']]
    missing = [str(p) for p in required if not p.is_file()]
    if not (aosp / 'out/host/linux-x86/lib64').is_dir():
        missing.append(str(aosp / 'out/host/linux-x86/lib64'))
    if not shutil.which('bash'):
        missing.append('bash on PATH')
    version()
    if missing:
        raise RuntimeError('Missing build dependencies:\n' + '\n'.join(missing))
    output = subprocess.check_output([str(java / 'bin/java'), '-version'], stderr=subprocess.STDOUT).decode()
    if 'version "1.8.' not in output:
        raise RuntimeError('OpenJDK 8 is required')
    for executable in required[2:4] + required[-3:]:
        if not os.access(executable, os.X_OK):
            raise RuntimeError('Not executable: ' + str(executable))
    print('Preflight OK: SDK 27, Java 8, Android host tools, signing inputs, vCard library')


def package(destination, aosp=None, java=None, rebuilt=False):
    meta = version()
    folder = destination / ('leo-apps-' + meta['version'])
    folder.mkdir(parents=True, exist_ok=False)
    for name in ['leo-visual.apk', 'leo-keypad-ime.apk']:
        shutil.copyfile(ROOT / 'output' / name, folder / name)
    shutil.copyfile(ROOT / 'install-app-bundle.py', folder / 'install-app-bundle.py')
    (folder / 'README.txt').write_text(
        'Leo ' + meta['version'] + '\nApp update bundle for an existing Leo Android 8.1 port.\n'
        'Verify offline: python install-app-bundle.py\n'
        'Update explicitly: python install-app-bundle.py --install --serial DEVICE_SERIAL --adb PATH_TO_ADB\n'
        'Default mode leo uses the existing /sbin/leo-framework-exec and reads its Java environment.\n'
        'Use --mode android only for a conventional Android ADB namespace.\n'
        'No ROM, boot, recovery, modem, user data or proprietary blobs are included.\n'
        'Both applications must already be installed. HOME/dialer/IME roles are preserved.\n'
        'Signing uses the existing AOSP development test key; preserve signer compatibility.\n'
        'An installation failure may leave only the first app updated; no automatic rollback.\n'
        'A clean-machine rebuild and bit-for-bit APK reproducibility are not claimed.\n', encoding='utf-8')
    files = {p.name: {'bytes': p.stat().st_size, 'sha256': sha(p)} for p in sorted(folder.iterdir())}
    tracked = subprocess.check_output(['git', '-C', str(ROOT.parent), 'ls-files', '-z',
        'android81/visual-shell', 'android81/keypad-ime', 'android81/localization', 'android81/build-visual-shell.sh',
        'android81/build-keypad-ime.sh', 'android81/release-version.json',
        'android81/release-apps.py', 'android81/install-app-bundle.py']).decode().split('\0')
    hashes = {name: sha(ROOT.parent / name) for name in tracked if name and (ROOT.parent / name).is_file()}
    inputs = {}
    if rebuilt:
        for name in ['prebuilts/sdk/27/android.jar', 'prebuilts/sdk/tools/lib/dx.jar',
                     'out/host/linux-x86/bin/aapt', 'out/host/linux-x86/bin/zipalign',
                     'out/host/linux-x86/framework/signapk.jar',
                     'build/target/product/security/testkey.x509.pem',
                     'out/target/common/obj/JAVA_LIBRARIES/com.android.vcard_intermediates/classes.jar']:
            inputs[name] = sha(aosp / name)
    data = {'format': 'leo-app-bundle', 'schema': 1, **meta, 'files': files,
            'rebuilt': rebuilt, 'build_input_sha256': inputs,
            'source_commit': subprocess.check_output(['git', '-C', str(ROOT.parent), 'rev-parse', 'HEAD']).decode().strip(),
            'working_tree_dirty': bool(subprocess.check_output(['git', '-C', str(ROOT.parent), 'status', '--porcelain'])),
            'source_sha256': hashes,
            'aosp_manifest_sha256': sha(ROOT / 'repro/aosp-manifest.xml')}
    (folder / 'manifest.json').write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    archive = Path(str(folder) + '.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as out:
        for path in sorted(folder.iterdir()):
            out.write(path, folder.name + '/' + path.name)
    print('Bundle: ' + str(folder))
    print('ZIP SHA-256: ' + sha(archive))
    return folder


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--aosp-root', type=Path, default=Path(os.environ.get('LEO_AOSP_ROOT', '/root/android81-leo/src')))
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('LEO_JAVA_HOME', '/usr/lib/jvm/java-8-openjdk-amd64')))
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--check', action='store_true')
    modes.add_argument('--build', action='store_true')
    modes.add_argument('--package-only', action='store_true')
    parser.add_argument('--output', type=Path, default=ROOT / 'output/releases')
    args = parser.parse_args()
    if not args.package_only:
        preflight(args.aosp_root, args.java_home)
    if args.build:
        env = dict(os.environ, LEO_AOSP_ROOT=str(args.aosp_root), LEO_JAVA_HOME=str(args.java_home))
        for script in ['build-keypad-ime.sh', 'build-visual-shell.sh']:
            subprocess.run(['bash', str(ROOT / script)], check=True, env=env)
    if args.build or args.package_only:
        package(args.output, args.aosp_root, args.java_home, args.build)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
