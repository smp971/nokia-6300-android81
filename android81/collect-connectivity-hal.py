"""Run in WSL: collect new native HAL dependencies without replacing core libraries."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess

root = Path(__file__).resolve().parent
product = Path('/root/android81-leo/src/out/target/product/leo/system')
present = set((root / 'output/connectivity-existing-libs.txt').read_text().split())
stage = root / 'output/connectivity-hal-stage'
files = {
    'bin/android.hardware.bluetooth@1.0-service': product / 'vendor/bin/hw/android.hardware.bluetooth@1.0-service',
    'bin/android.hardware.wifi@1.0-service': product / 'vendor/bin/hw/android.hardware.wifi@1.0-service',
    'bin/wpa_supplicant': product / 'vendor/bin/hw/wpa_supplicant',
    'bin/wpa_cli': product / 'vendor/bin/wpa_cli',
    'bin/hostapd': product / 'vendor/bin/hostapd',
    'hw/android.hardware.bluetooth@1.0-impl.so': product / 'vendor/lib/hw/android.hardware.bluetooth@1.0-impl.so',
}
queue = list(files.values())
visited = set()
while queue:
    path = queue.pop()
    assert path.is_file(), path
    if path in visited:
        continue
    visited.add(path)
    needed = re.findall(r'\(NEEDED\).*\[([^]]+)\]', subprocess.check_output(['readelf', '-d', str(path)], text=True))
    for name in needed:
        if name in present or 'lib/' + name in files:
            continue
        candidates = [product / 'lib' / name, product / 'vendor/lib' / name]
        source = next((p for p in candidates if p.is_file()), None)
        assert source, name
        files['lib/' + name] = source
        queue.append(source)
report = {}
for relative, source in files.items():
    destination = stage / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    report[relative] = hashlib.sha256(destination.read_bytes()).hexdigest()
(stage / 'manifest.json').write_text(json.dumps(report, indent=2))
print('Collected', len(files), 'new HAL trial files; existing core libraries excluded')
