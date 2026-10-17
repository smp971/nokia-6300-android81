"""Bundle the private radio trial for one transfer; --start launches collection."""
from pathlib import Path
import argparse
import shlex
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent
OUT = ROOT/'output'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']


def shell(*args):
    return subprocess.check_output(ADB+['shell', shlex.join([
        '/sbin/leo-framework-exec', *args])], timeout=25)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--start', action='store_true')
    args = parser.parse_args()
    # All prerequisites are local and excluded from Git, including proprietary blobs.
    assert (ROOT/'backup-live/radio-before-bringup/checksums.json').is_file()
    files = [(p, 'leo-radio-probe/lib/'+p.name, 0o644)
             for p in sorted((OUT/'radio-probe/lib').glob('*.so'))]
    assert len(files) >= 20, 'Run stage-radio-probe.py to prepare private dependencies first.'
    assert not any(Path(name).name in ('libril.so', 'librilutils.so') for _, name, _ in files)
    files += [(OUT/'radio-library-probe', 'leo-radio-probe/probe', 0o755),
              (OUT/'radio-dms-probe', 'leo-radio-dms', 0o755),
              (OUT/'radio-ipc/lib/libqmi_cci.so', 'leo-radio-ipc/libqmi_cci.so', 0o644),
              (OUT/'radio-ipc-shim.so', 'leo-radio-ipc/shim.so', 0o644),
              (ROOT/'radio-start-probe.sh', 'leo-radio-start.sh', 0o644),
              (ROOT/'radio-collect-trial.sh', 'leo-radio-collect.sh', 0o644)]
    for name in ('rmt_storage', 'qmuxd', 'pm-service', 'irsc_util'):
        files.append((ROOT.parent/'analysis/system/bin'/name, 'leo-radio-probe/'+name, 0o755))
    files.append((ROOT.parent/'analysis/system/etc/sec_config', 'leo-radio-probe/sec_config', 0o644))
    for path, _, _ in files:
        assert path.is_file(), path
    bundle = OUT/'radio-collection.tar'
    with tarfile.open(bundle, 'w') as archive:
        for path, name, mode in files:
            info = archive.gettarinfo(str(path), arcname=name)
            info.mode, info.uid, info.gid = mode, 0, 0
            info.uname = info.gname = ''
            with path.open('rb') as stream:
                archive.addfile(info, stream)
    print(f'Prepared private bundle: {bundle} ({bundle.stat().st_size} bytes)', flush=True)
    if args.start:
        processes = shell('/system/bin/ps', '-A', '-o', 'PID,ARGS').decode()
        for line in processes.splitlines():
            fields = line.split(None, 1)
            vendor = any(path in line for path in ('/tmp/leo-radio-probe/', '/system/vendor/leo-radio/'))
            if len(fields) == 2 and fields[1].startswith('rild ') and vendor:
                raise SystemExit('Vendor radio services are already active; do not overlap trials.')
            if len(fields) == 2 and fields[1].startswith(('sh ', '/system/bin/sh ')) and '/tmp/leo-radio-collect.sh' in fields[1]:
                raise SystemExit('A collector is already active; retrieve its evidence first.')
        subprocess.run(ADB+['push', str(bundle), '/tmp/leo-radio-collection.tar'], check=True)
        shell('/system/bin/tar', '-xf', '/tmp/leo-radio-collection.tar', '-C', '/tmp')
        command = ('/system/bin/sh /tmp/leo-radio-collect.sh '
                   '</dev/null >/tmp/leo-radio-collector.log 2>&1 &')
        shell('/system/bin/sh', '-c', command)
        print('Detached collection launched. Pull /tmp/leo-radio-evidence after COMPLETE.')
