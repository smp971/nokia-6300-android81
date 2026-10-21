"""Verify a Leo app bundle; installation requires --install and an explicit serial."""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import subprocess


def verify(folder):
    folder = Path(folder)
    data = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
    if data.get('format') != 'leo-app-bundle' or data.get('schema') != 1:
        raise ValueError('Unsupported bundle format')
    expected = {'leo-visual.apk', 'leo-keypad-ime.apk', 'install-app-bundle.py', 'README.txt'}
    if set(data['files']) != expected:
        raise ValueError('Unexpected bundle file list')
    for name, info in data['files'].items():
        content = (folder / name).read_bytes()
        if len(content) != info['bytes'] or hashlib.sha256(content).hexdigest() != info['sha256']:
            raise ValueError('Hash/size mismatch: ' + name)
    return data


def install(folder, adb, serial, mode):
    folder = Path(folder)
    verify(folder)

    def run(*args):
        return subprocess.check_output([adb, '-s', serial, *args], timeout=90).decode(errors='replace').strip()

    wrapper = ['/sbin/leo-framework-exec'] if mode == 'leo' else []

    def bare(*args):
        return run('shell', shlex.join(wrapper + list(args)))

    environment = []
    if mode == 'leo':
        for line in bare('/system/bin/cat', '/init.environ.rc').splitlines():
            fields = line.split()
            if len(fields) == 3 and fields[0] == 'export':
                environment.append(fields[1] + '=' + fields[2])
        if not any(x.startswith('BOOTCLASSPATH=') for x in environment):
            raise RuntimeError('Android Java environment unavailable; nothing installed')

    def framework(*args):
        return bare('/system/bin/env', *environment, *args)

    if bare('/system/bin/getprop', 'ro.build.version.sdk') != '27':
        raise RuntimeError('This bundle targets Android API 27; nothing installed')
    for package in ['ro.leo.keypad', 'ro.leo.visual']:
        if 'package:' not in framework('/system/bin/sh', '/system/bin/pm', 'path', package):
            raise RuntimeError('Existing Leo installation required: ' + package)
    for name in ['leo-keypad-ime.apk', 'leo-visual.apk']:
        destination = ('/tmp/' if mode == 'leo' else '/data/local/tmp/') + name
        run('push', str(folder / name), destination)
        output = framework('/system/bin/sh', '/system/bin/pm', 'install', '-r', destination)
        if 'Success' not in output:
            raise RuntimeError('Install failed: ' + name + '\n' + output)
        print(name + ': Success')
    print('Apps updated. Default HOME, dialer, IME and system partitions were not changed.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--serial')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--mode', choices=['leo', 'android'], default='leo')
    args = parser.parse_args()
    data = verify(args.bundle)
    print('Verified Leo ' + data['version'] + ': four file hashes match')
    if args.install:
        if not args.serial:
            parser.error('--install requires --serial DEVICE_SERIAL')
        install(args.bundle, args.adb, args.serial, args.mode)


if __name__ == '__main__':
    main()
