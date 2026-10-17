"""Redirect the stock EGL vendor-directory strings in a private probe copy."""
from pathlib import Path
import hashlib
import json
here = Path(__file__).resolve().parent
source = here.parent / 'analysis/system/vendor/lib/egl/libEGL_adreno.so'
data = source.read_bytes()
assert hashlib.sha256(data).hexdigest() == '4666a73f1b1758d77ab36588db721bf5beb86cdd8af95b0e7154aebc2524eb5c'
old = b'/vendor/lib/\0'
new = b'/tmp/leog/\0'
assert data.count(old) == 2
patched = data.replace(old, new.ljust(len(old), b'\0'))
assert len(patched) == len(data)
output = here / 'output/adreno-private/libEGL_adreno.so'
output.parent.mkdir(exist_ok=True)
output.write_bytes(patched)
report = {'source_sha256': hashlib.sha256(data).hexdigest(),
          'patched_sha256': hashlib.sha256(patched).hexdigest(),
          'replacements': 2, 'installed_system_modified': False}
(output.parent / 'patch.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
