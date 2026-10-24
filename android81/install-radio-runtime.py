"""Prepare/install the validated private radio runtime on internal system only."""
from pathlib import Path
import argparse
import hashlib
import json
import shlex
import subprocess
import tarfile
import time

ROOT = Path(__file__).resolve().parent
OUT = ROOT/'output'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']


def shell(*args, timeout=20):
    result = subprocess.run(ADB+['shell', shlex.join([
        '/sbin/leo-framework-exec', *args])], capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError((result.stdout+result.stderr).decode(errors='replace'))
    return result.stdout.decode(errors='replace').strip()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--install', action='store_true')
    args = parser.parse_args()
    assert (ROOT/'backup-live/radio-before-bringup/checksums.json').is_file()
    base = 'vendor/leo-radio/'
    files = {base+'lib/'+p.name: (p, 0o644)
             for p in sorted((OUT/'radio-probe/lib').glob('*.so'))}
    assert len(files) >= 20
    assert base+'lib/libril.so' not in files
    assert base+'lib/librilutils.so' not in files
    files[base+'lib/libqmi_cci.so'] = (OUT/'radio-ipc/lib/libqmi_cci.so', 0o644)
    files[base+'shim.so'] = (OUT/'radio-ipc-shim.so', 0o644)
    files[base+'start.sh'] = (ROOT/'radio-runtime/start.sh', 0o755)
    files[base+'recover.sh'] = (ROOT/'radio-runtime/recover.sh', 0o755)
    files['etc/init/leo-radio.rc'] = (ROOT/'radio-runtime/init.leo.radio.rc', 0o644)
    files['etc/init/leo-radio-recovery.rc'] = (ROOT/'radio-runtime/init.leo.radio-recovery.rc', 0o644)
    for name in ('rmt_storage', 'pm-service', 'irsc_util'):
        files[base+'bin/'+name] = (ROOT.parent/'analysis/system/bin'/name, 0o755)
    files[base+'sec_config'] = (ROOT.parent/'analysis/system/etc/sec_config', 0o644)
    expected = {name: hashlib.sha256(path.read_bytes()).hexdigest()
                for name, (path, _) in files.items()}
    bundle = OUT/'radio-runtime-install.tar'
    with tarfile.open(bundle, 'w') as archive:
        entries = [('files/'+name, path, mode) for name, (path, mode) in files.items()]
        entries.append(('install-job.sh', ROOT/'radio-runtime/install-job.sh', 0o644))
        for name, path, mode in entries:
            info = archive.gettarinfo(str(path), arcname=name)
            info.mode, info.uid, info.gid = mode, 0, 0
            info.uname = info.gname = ''
            with path.open('rb') as stream:
                archive.addfile(info, stream)
    report = {'phase': 'prepared', 'sha256': expected,
              'boot_recovery_written': False, 'userdata_formatted': False}
    report_path = OUT/'radio-runtime-install.json'
    report_path.write_text(json.dumps(report, indent=2))
    print(f'Prepared {len(files)} private runtime files: {bundle}', flush=True)
    if not args.install:
        raise SystemExit(0)
    assert 'androidboot.serialno=695d' in shell('/system/bin/cat', '/proc/cmdline')
    assert shell('/system/bin/cat', '/leo-framework-internal') == 'Validated internal Android partitions.'
    assert shell('/system/bin/getprop', 'ro.boot.hwid') == '0x0'
    assert '/dev/leo-internal-system /system ' in shell('/system/bin/cat', '/proc/1/mounts')
    assert shell('/system/bin/sh', '-c', 'test ! -e /system/vendor/leo-radio && test ! -e /system/etc/init/leo-radio.rc; echo $?') == '0'
    available = int(shell('/system/bin/df', '-k', '/system').splitlines()[-1].split()[3])*1024
    assert available > bundle.stat().st_size+8*1024*1024
    subprocess.run(ADB+['push', str(bundle), '/tmp/leo-radio-runtime-install.tar'], check=True)
    assert shell('/system/bin/sha256sum', '/tmp/leo-radio-runtime-install.tar').split()[0] == hashlib.sha256(bundle.read_bytes()).hexdigest()
    shell('/system/bin/mkdir', '-p', '/tmp/leo-radio-install')
    shell('/system/bin/tar', '-xf', '/tmp/leo-radio-runtime-install.tar', '-C', '/tmp/leo-radio-install')
    shell('/system/bin/sh', '-c', '/system/bin/sh /tmp/leo-radio-install/install-job.sh '
          '</dev/null >/tmp/leo-radio-install/log 2>&1 &')
    deadline = time.monotonic()+75
    status = ''
    while time.monotonic() < deadline:
        try:
            status = shell('/system/bin/cat', '/tmp/leo-radio-install/status', timeout=5)
        except (RuntimeError, subprocess.TimeoutExpired):
            time.sleep(2)
            continue
        if status in ('COMPLETE', 'FAILED'):
            break
        time.sleep(2)
    assert status == 'COMPLETE', f'Installation status: {status}. Read /tmp/leo-radio-install/log.'
    hashes = shell('/system/bin/sha256sum', *['/system/'+name for name in files])
    actual = {line.split(None, 1)[1].strip().removeprefix('/system/'): line.split()[0]
              for line in hashes.splitlines()}
    assert actual == expected
    assert '/system ext4 ro,' in shell('/system/bin/cat', '/proc/1/mounts')
    report['phase'] = 'installed-hashes-verified-reboot-pending'
    report_path.write_text(json.dumps(report, indent=2))
    print('Verified all installed hashes; system is read-only. Reboot required for init services.')
