"""Read and verify stock boot/system before any internal Android installation."""
from pathlib import Path
import hashlib
import json
import subprocess

HERE = Path(__file__).resolve().parent
DEST = HERE/'backup-live/kaios-before-internal'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
BB = '/system/bin/busybox '

def call(command):
    result = subprocess.run(ADB+['shell', command], capture_output=True, text=True,
                            encoding='utf-8', errors='replace', timeout=180)
    if result.returncode:
        raise RuntimeError(result.stdout+result.stderr)
    return result.stdout.strip()

def hashes(path):
    sha, md5 = hashlib.sha256(), hashlib.md5()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1048576), b''):
            sha.update(chunk); md5.update(chunk)
    return {'sha256': sha.hexdigest(), 'md5': md5.hexdigest(), 'bytes': path.stat().st_size}

assert 'uid=0' in call(BB+'id')
assert 'androidboot.serialno=695d' in call(BB+'cat /proc/cmdline')
DEST.mkdir(parents=True, exist_ok=True)
report = {'target_serial': '695d', 'read_only_backup': True, 'partitions': {}}
original = json.loads((HERE/'backup-live/checksums.json').read_text())
for name, number, expected in [('boot', 29, 33554432), ('system', 30, 838860800)]:
    info = dict(line.split('=', 1) for line in call(BB+f'cat /sys/class/block/mmcblk0p{number}/uevent').splitlines() if '=' in line)
    assert info['PARTNAME'] == name and info['MAJOR'] == '179' and int(info['MINOR']) == number
    assert int(call(BB+f'cat /sys/class/block/mmcblk0p{number}/size'))*512 == expected
    node = '/dev/leo-backup-'+name
    call('if [ ! -b '+node+' ]; then '+BB+f'mknod {node} b 179 {number}; fi')
    assert call(BB+'stat -c %t:%T '+node) == f'b3:{number:x}'
    device_md5 = call(BB+'md5sum '+node).split()[0]
    if name == 'boot':
        assert device_md5 == original['boot']['md5'], 'Boot is no longer the known stock backup'
    path = DEST/(name+'-kaios.img')
    if not path.exists():
        temporary = path.with_suffix('.incoming')
        assert not temporary.exists(), 'Preserve existing incomplete backup for review'
        print('Reading '+name+' to PC...', flush=True)
        # The early adbd shell changes binary newlines even with -T. Use the
        # binary-safe sync protocol, which supports these block-device nodes.
        result = subprocess.run(ADB+['pull', node, str(temporary)],
                                capture_output=True, timeout=900)
        assert result.returncode == 0, result.stderr.decode(errors='replace')
        actual = hashes(temporary)
        assert actual['bytes'] == expected and actual['md5'] == device_md5
        temporary.rename(path)
    actual = hashes(path)
    assert actual['bytes'] == expected and actual['md5'] == device_md5
    assert call(BB+'md5sum '+node).split()[0] == actual['md5']
    report['partitions'][name] = dict(actual, path=str(path), device_md5=device_md5)
    (DEST/'checksums.json').write_text(json.dumps(report, indent=2))
    print(name+' backup verified: '+actual['sha256'], flush=True)
print(json.dumps(report, indent=2))
