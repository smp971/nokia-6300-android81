"""Use Canvas for Settings on Leo's CPU-only graphics bring-up."""
from pathlib import Path

path = Path('/root/android81-leo/src/packages/apps/Settings/AndroidManifest.xml')
source = path.read_text()
old = 'android:hardwareAccelerated="true"'
new = 'android:hardwareAccelerated="false"'
if old in source:
    assert source.count(old) == 1
    path.write_text(source.replace(old, new))
else:
    assert new in source
