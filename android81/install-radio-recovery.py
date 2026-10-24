"""Install only the bounded radio recovery script/init rule; no RIL restart or flash."""
from pathlib import Path
import argparse, hashlib, json, subprocess, time

ROOT = Path(__file__).resolve().parent
FW = ['python', '-X', 'utf8', str(ROOT/'framework-command.py')]
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
FILES = {
    '/system/vendor/leo-radio/recover.sh': (ROOT/'radio-runtime/recover.sh', '755'),
    '/system/etc/init/leo-radio-recovery.rc': (ROOT/'radio-runtime/init.leo.radio-recovery.rc', '644'),
}


def run(*args):
    return subprocess.check_output(FW+list(args), timeout=35).decode(errors='replace').strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true')
    parser.add_argument('--startup', action='store_true', help='Also update early radio startup script and init triggers; takes effect on reboot')
    args = parser.parse_args()
    if args.startup:
        FILES.update({
            '/system/vendor/leo-radio/start.sh': (ROOT/'radio-runtime/start.sh', '755'),
            '/system/etc/init/leo-radio.rc': (ROOT/'radio-runtime/init.leo.radio.rc', '644'),
        })
    expected = {target: hashlib.sha256(source.read_bytes()).hexdigest()
                for target, (source, _) in FILES.items()}
    if not args.install:
        print(json.dumps(expected, indent=2))
        return
    assert 'androidboot.serialno=695d' in run('/system/bin/cat', '/proc/cmdline')
    assert run('/system/bin/getprop', 'ro.boot.hwid') == '0x0'
    assert run('/system/bin/cat', '/leo-framework-internal') == 'Validated internal Android partitions.'
    import re
    states = re.findall(r'^\s*mCallState=(\d+)', run('/system/bin/dumpsys', 'telephony.registry'), re.M)
    assert len(states) == 2 and set(states) == {'0'}, 'Finish calls first'
    assert '/dev/leo-internal-system /system ' in run('/system/bin/cat', '/proc/1/mounts')
    backup = ROOT/'output/radio-recovery-backups'/time.strftime('%Y%m%d-%H%M%S')
    backup.mkdir(parents=True, exist_ok=False)
    before = {}
    for index, target in enumerate(FILES):
        exists = run('/system/bin/sh', '-c', 'if [ -f '+target+' ]; then echo yes; else echo no; fi')
        before[target] = None
        if exists == 'yes':
            remote = '/tmp/leo-radio-recovery-before-'+str(index)
            run('/system/bin/cp', target, remote)
            local = backup/('before-'+str(index))
            subprocess.run(ADB+['pull', remote, str(local)], check=True, capture_output=True)
            before[target] = hashlib.sha256(local.read_bytes()).hexdigest()
    (backup/'before.json').write_text(json.dumps(before, indent=2))
    for index, (target, (source, _)) in enumerate(FILES.items()):
        staged = '/tmp/leo-radio-recovery-new-'+str(index)
        subprocess.run(ADB+['push', str(source), staged], check=True, capture_output=True)
        assert run('/system/bin/sha256sum', staged).split()[0] == expected[target]
    # One short shell transaction; restore read-only even if a copy fails.
    job = ['set -eu', "trap 'mount -o remount,ro /system' EXIT", 'mount -o remount,rw /system']
    for index, (target, (_, mode)) in enumerate(FILES.items()):
        staged = '/tmp/leo-radio-recovery-new-'+str(index)
        job += ['cp '+staged+' '+target+'.leo-new', 'chown 0:0 '+target+'.leo-new',
                'chmod '+mode+' '+target+'.leo-new', 'mv '+target+'.leo-new '+target]
    run('/system/bin/sh', '-c', '\n'.join(job))
    for target, digest in expected.items():
        assert run('/system/bin/sha256sum', target).split()[0] == digest
    assert '/system ext4 ro,' in run('/system/bin/cat', '/proc/1/mounts')
    state = run('/system/bin/sh', '/system/vendor/leo-radio/recover.sh', '--check')
    (backup/'installed.json').write_text(json.dumps({'sha256': expected, 'live_check': state,
        'boot_hook_reboot_tested': False, 'radio_restarted_by_installer': False}, indent=2))
    print('Installed recovery guards; hashes verified, system read-only; live check: '+state)
    print('Init boot hook is installed; next-boot behavior has not been tested.')


if __name__ == '__main__':
    main()
