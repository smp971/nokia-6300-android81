"""Back up and update selected Leo Android system APKs, with timed rollback.

No boot image, framework jar, modem, resource package or application data writes.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shlex
import struct
import subprocess
import tarfile
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
BACKUP = ROOT / 'output/system-keypad-before'
ACTIVE = ROOT / 'output/system-keypad-active.json'
ACK = '/tmp/leo-system-keypad-ack-none'
PACKAGES = [('com.android.systemui', 'SystemUI.apk'), ('com.android.settings', 'Settings.apk'),
            ('com.android.packageinstaller', 'PackageInstaller.apk')]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode', choices=['backup', 'install', 'ack', 'rollback'])
parser.add_argument('--only', choices=['both', 'all', 'systemui', 'settings', 'packageinstaller'], default='both')
parser.add_argument('--rollback-seconds', type=int, default=240,
                    help='Bounded test window before restoring APKs (60..900 seconds)')
parser.add_argument('--backup-dir',type=Path,default=BACKUP,
                    help='Separate verified baseline for a new development milestone')
args = parser.parse_args()
BACKUP=args.backup_dir.resolve()
assert 60 <= args.rollback_seconds <= 900, 'Rollback window must be 60..900 seconds'
previous_ack = None
if args.mode == 'install' and ACTIVE.exists():
    previous_ack = json.loads(ACTIVE.read_text(encoding='utf-8'))['ack']
    assert re.fullmatch(r'/tmp/leo-system-keypad-ack-[a-z0-9-]+', previous_ack)
if args.only == 'both':
    PACKAGES = PACKAGES[:2]
elif args.only != 'all':
    PACKAGES = [row for row in PACKAGES if row[0] == 'com.android.' + args.only]
if args.mode in ('ack', 'rollback') and ACTIVE.exists():
    ACK = json.loads(ACTIVE.read_text(encoding='utf-8'))['ack']
    assert re.fullmatch(r'/tmp/leo-system-keypad-ack-[a-z0-9-]+', ACK)


def fw(*command, timeout=90):
    return subprocess.check_output(['python', '-X', 'utf8', str(ROOT / 'framework-command.py'),
                                    '--timeout', str(timeout), *command], timeout=timeout + 5)


def push(local, remote):
    subprocess.run(ADB + ['push', str(local), remote], check=True, capture_output=True, timeout=45)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def installed(package):
    value = fw('/system/bin/sh', '/system/bin/pm', 'path', package).decode().strip()
    if not value.startswith('package:') or '\n' in value:
        raise RuntimeError('Expected one installed APK: ' + package)
    return value[8:]


def checksum(path):
    return fw('/system/bin/sha256sum', path).decode().split()[0]


def reload_system_ui():
    pid = fw('/system/bin/pidof', 'com.android.systemui').decode().strip()
    if pid.isdigit() and fw('/system/bin/cat', '/proc/' + pid + '/cmdline').startswith(b'com.android.systemui\0'):
        fw('/system/bin/kill', '-15', pid)
    # Android O rejects background service startup while its UID has no active
    # wallpaper/window. This exemption expires itself and changes no saved policy.
    fw('/system/bin/cmd', 'deviceidle', 'tempwhitelist', '-d', '20000', 'com.android.systemui')
    # Wallpaper may restart the process before its package/UID record is ready.
    for attempt in range(4):
        time.sleep(3)
        try:
            fw('/system/bin/sh', '/system/bin/am', 'startservice', '--user', '0',
               '-n', 'com.android.systemui/.SystemUIService')
            return
        except subprocess.CalledProcessError:
            if attempt == 3:
                raise


def has_helper(apk):
    descriptor = b'Lcom/android/internal/policy/LeoKeypadNavigation;'
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r'classes[0-9]*\.dex', name):
                continue
            data = archive.read(name)
            strings = struct.unpack_from('<I', data, 60)[0]
            types = struct.unpack_from('<I', data, 68)[0]
            count, classes = struct.unpack_from('<II', data, 96)
            for n in range(count):
                index = struct.unpack_from('<I', data, classes + n * 32)[0]
                string = struct.unpack_from('<I', data, types + index * 4)[0]
                offset = struct.unpack_from('<I', data, strings + string * 4)[0]
                while data[offset] & 128:
                    offset += 1
                offset += 1
                if data[offset:data.index(b'\0', offset)] == descriptor:
                    return True
    return False


assert 'androidboot.serialno=695d' in fw('/system/bin/cat', '/proc/cmdline').decode()
assert 'Validated internal Android partitions.' in fw('/system/bin/cat', '/leo-framework-internal').decode()
BACKUP.mkdir(exist_ok=True)
manifest_path = BACKUP / 'manifest.json'
manifest = json.loads(manifest_path.read_text(encoding='utf-8')) if manifest_path.exists() else {}
if args.mode == 'backup':
    for package, filename in PACKAGES:
        if package in manifest:
            continue
        path = installed(package)
        expected = checksum(path)
        remote = '/tmp/leo-system-before-' + filename
        fw('/system/bin/cp', path, remote)
        subprocess.run(ADB + ['pull', remote, str(BACKUP / filename)], check=True,
                       capture_output=True, timeout=45)
        assert digest(BACKUP / filename) == expected
        manifest[package] = {'path': path, 'sha256': expected, 'file': filename}
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
for package, filename in PACKAGES:
    assert package in manifest, 'Create a verified backup first: ' + package
    assert digest(BACKUP / filename) == manifest[package]['sha256'], 'Backup hash mismatch'

if args.mode == 'backup':
    print('Verified original APK backups for selected packages; no system changes')
elif args.mode == 'ack':
    assert ACTIVE.exists(), 'No timed installation to acknowledge'
    fw('/system/bin/touch', ACK)
    print('Native keypad update accepted; timed rollback cancelled')
else:
    if args.mode == 'install':
        for package, filename in PACKAGES:
            candidate = ROOT / 'output/framework-keypad' / filename
            assert candidate.is_file() and has_helper(candidate), 'App-local helper missing: ' + filename
            win = candidate.resolve().as_posix()
            linux = '/mnt/' + win[0].lower() + win[2:]
            badging = subprocess.check_output(['wsl', '-d', 'kaios', '--',
                      '/root/android81-leo/src/out/host/linux-x86/bin/aapt', 'dump', 'badging', linux])
            assert ("package: name='" + package + "'").encode() in badging, 'Wrong APK identity'
    states = re.findall(r'mCallState=(\d+)', fw('/system/bin/sh', '-c',
                        'dumpsys telephony.registry').decode())
    assert states and all(value == '0' for value in states), 'Close calls before updating system UI'
    fw('/system/bin/setprop', 'persist.leo.keypad_ui', '0')
    for package, filename in PACKAGES:
        push(BACKUP / filename, '/tmp/leo-system-before-' + filename)
    if args.mode == 'rollback':
        for package, filename in PACKAGES:
            result = fw('/system/bin/sh', '/system/bin/pm', 'install', '-r', '-d',
                        '/tmp/leo-system-before-' + filename)
            assert b'Success' in result, package
        fw('/system/bin/touch', ACK)
        reload_system_ui()
        print('Original APKs restored; keypad adaptation disabled; application data retained')
    else:
        ACK = '/tmp/leo-system-keypad-ack-' + str(uuid.uuid4())
        for package, filename in PACKAGES:
            candidate = ROOT / 'output/framework-keypad' / filename
            assert has_helper(candidate), 'Candidate must include app-local keypad class: ' + filename
            push(candidate, '/tmp/leo-system-candidate-' + filename)
            assert checksum('/tmp/leo-system-candidate-' + filename) == digest(candidate)
        with tarfile.open(ROOT / 'output/framework-root.tar') as archive:
            source = archive.extractfile('./init.environ.rc').read().decode()
        exports = []
        for line in source.splitlines():
            fields = line.split()
            if len(fields) == 3 and fields[0] == 'export':
                exports.append('export ' + fields[1] + '=' + shlex.quote(fields[2]))
        watchdog = '\n'.join(['#!/system/bin/sh', *exports, '/system/bin/sleep ' + str(args.rollback_seconds),
                              'test -f ' + ACK + ' && exit 0',
                              '/system/bin/setprop persist.leo.keypad_ui 0',
                              *['/system/bin/sh /system/bin/pm install -r -d /tmp/leo-system-before-' + name
                                for _, name in PACKAGES], '/system/bin/sync', ''])
        local = ROOT / 'output/system-keypad-rollback.sh'
        local.write_text(watchdog, encoding='utf-8', newline='\n')
        remote_script = ACK.replace('-ack-', '-rollback-') + '.sh'
        push(local, remote_script)
        ACTIVE.write_text(json.dumps({'ack': ACK}), encoding='utf-8')
        fw('/system/bin/rm', '-f', ACK)
        fw('/system/bin/sh', '-c', '/system/bin/nohup /system/bin/sh ' + remote_script +
           ' </dev/null >/tmp/leo-system-keypad-rollback.log 2>&1 &')
        if previous_ack:
            # The new watchdog now protects the update. Prevent an older test
            # timer from restoring APKs during this attempt; scripts are unique.
            fw('/system/bin/touch', previous_ack)
        for package, filename in PACKAGES:
            print('Installing ' + package + ' (data retained)', flush=True)
            result = fw('/system/bin/sh', '/system/bin/pm', 'install', '-r',
                        '/tmp/leo-system-candidate-' + filename)
            assert b'Success' in result, 'Package install failed: ' + package
            assert checksum(installed(package)) == digest(ROOT / 'output/framework-keypad' / filename)
        # Persistent SystemUI keeps stale asset contexts after Settings APK replacement.
        # Reload it once only after every requested package session has completed.
        reload_system_ui()
        fw('/system/bin/setprop', 'persist.leo.keypad_ui', '1')
        print(', '.join(name for _, name in PACKAGES) +
              ' installed; timed rollback in ' + str(args.rollback_seconds) + ' seconds unless ack follows checks')
