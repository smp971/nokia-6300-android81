"""Preserve local KaiOS reference packages, verifying every copied file by SHA-256."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'analysis/system/b2g/webapps'
DEST = ROOT / 'android81/local/kaios-apps'
registry = json.loads((SOURCE / 'webapps.json').read_text(encoding='utf-8-sig'))
DEST.mkdir(parents=True, exist_ok=True)
records = []
for app_id in sorted(registry):
    assert Path(app_id).name == app_id and app_id not in ('.', '..') and '/' not in app_id and '\\' not in app_id
    files = []
    for name in ('application.zip', 'manifest.webapp'):
        source = SOURCE / app_id / name
        if not source.is_file():
            continue
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        target = DEST / app_id / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            assert hashlib.sha256(target.read_bytes()).hexdigest() == digest, f'Existing copy differs: {target}'
        else:
            shutil.copy2(source, target)
        assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
        files.append(dict(file=name, bytes=target.stat().st_size, sha256=digest))
    records.append(dict(id=app_id, files=files))
registry_source=SOURCE / 'webapps.json'
registry_target=DEST / 'webapps.json'
if registry_target.exists():
    assert registry_target.read_bytes()==registry_source.read_bytes(), 'Existing registry copy differs'
else:
    shutil.copy2(registry_source, registry_target)
(DEST / 'checksums.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
clock = DEST / 'clock.gaiamobile.org/application.zip'
with zipfile.ZipFile(clock) as archive:
    names = archive.namelist()
    relevant = [name for name in names if any(word in name.lower() for word in ('stopwatch', 'alarm', 'timer'))]
print(f'Local reference: {DEST}')
print(f'{sum(any(f["file"] == "application.zip" for f in r["files"]) for r in records)} archives verified; {len(records)} registry entries')
print('Clock reference paths:', ', '.join(relevant[:20]))
