"""Back up and verify live radio state before starting vendor daemons."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parent
OUT = ROOT/'backup-live/radio-before-bringup'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
EXEC = '/sbin/leo-framework-exec /system/bin/'
OUT.mkdir(parents=True, exist_ok=True)
report = {}
for number, name in ((15, 'modemst1'), (16, 'modemst2'), (18, 'fsc'),
                     (27, 'fsg'), (31, 'persist')):
    info = subprocess.check_output(ADB+['shell', EXEC+
        f'cat /sys/class/block/mmcblk0p{number}/uevent']).decode()
    assert 'PARTNAME='+name in info
    node = '/tmp/leo-backup-'+name
    subprocess.run(ADB+['shell', EXEC+f'mknod {node} b 179 {number}'],
                   capture_output=True)  # Existing identical nodes are allowed.
    size = int(subprocess.check_output(ADB+['shell', EXEC+
        f'cat /sys/class/block/mmcblk0p{number}/size']))*512
    remote = subprocess.check_output(ADB+['shell', EXEC+'md5sum '+node]).decode().split()[0]
    path = OUT/(name+'.img')
    if path.exists():
        assert path.stat().st_size == size and hashlib.md5(path.read_bytes()).hexdigest() == remote
    else:
        subprocess.run(ADB+['pull', node, str(path)], check=True)
    data = path.read_bytes()
    assert len(data) == size and hashlib.md5(data).hexdigest() == remote
    report[name] = dict(bytes=size, md5=remote, sha256=hashlib.sha256(data).hexdigest())
(OUT/'checksums.json').write_text(json.dumps(report, indent=2))
print('Verified all five radio-state backups locally; no partition writes.')
