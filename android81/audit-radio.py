"""Read-only radio bring-up inventory; keep device logs and blobs local."""
from pathlib import Path
import json
import re
import struct
import subprocess

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'android81/output/radio-audit'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']


def needed(path):
    """Read DT_NEEDED from the stock little-endian ELF32 ARM binaries."""
    data = path.read_bytes()
    assert data[:6] == b'\x7fELF\x01\x01', path
    phoff = struct.unpack_from('<I', data, 28)[0]
    entsize, count = struct.unpack_from('<HH', data, 42)
    headers = [struct.unpack_from('<8I', data, phoff+i*entsize) for i in range(count)]
    dynamic = next(h for h in headers if h[0] == 2)
    entries = [struct.unpack_from('<II', data, i)
               for i in range(dynamic[1], dynamic[1]+dynamic[4], 8)]
    address = next(value for tag, value in entries if tag == 5)
    segment = next(h for h in headers if h[0] == 1 and h[2] <= address < h[2]+h[4])
    strings = segment[1]+address-segment[2]
    return [data[strings+value:].split(b'\0', 1)[0].decode()
            for tag, value in entries if tag == 1]


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    stock = ROOT / 'analysis/system'
    libraries = {p.name: p for folder in ('lib', 'vendor/lib')
                 for p in (stock/folder).glob('*.so')}
    queue = [stock/'bin'/name for name in ('rmt_storage', 'qmuxd', 'netmgrd')]
    queue.append(stock/'vendor/lib/libril-qc-qmi-1.so')
    graph = {}
    while queue:
        path = queue.pop()
        if str(path.relative_to(stock)) in graph:
            continue
        deps = needed(path)
        graph[str(path.relative_to(stock))] = deps
        queue.extend(libraries[name] for name in deps if name in libraries)
    (OUT/'stock-dependencies.json').write_text(json.dumps(graph, indent=2))
    commands = {
        'properties': '/sbin/leo-framework-exec /system/bin/getprop',
        'subsystems': 'for d in /sys/bus/msm_subsys/devices/subsys*; do cat $d/name $d/state; done',
        'partitions': 'for d in /sys/class/block/mmcblk0p*; do cat $d/uevent; done',
        'mounts': '/sbin/leo-framework-exec /system/bin/cat /proc/1/mounts',
        'kernel': '/sbin/leo-framework-exec /system/bin/dmesg',
        'radio-log': '/sbin/leo-framework-exec /system/bin/logcat -b radio -d -t 100',
    }
    for name, command in commands.items():
        result = subprocess.run(ADB+['shell', command], capture_output=True, timeout=25)
        (OUT/(name+'.txt')).write_bytes(result.stdout+result.stderr)
        assert result.returncode == 0, name
    print(f'Inventoried {len(graph)} stock ELF dependencies. Reports: {OUT}')
