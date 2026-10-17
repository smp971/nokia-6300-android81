"""Stage additive GPU files and verified graphics backups; do not switch HALs."""
from pathlib import Path
import hashlib
import json
import shlex
import shutil
import subprocess

here = Path(__file__).resolve().parent
out = here / 'output'
stage = out / 'adreno-integration-stage'
stage.mkdir(exist_ok=True)
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
def call(*args):
    r = subprocess.run(adb + list(args), capture_output=True, text=True,
        encoding='utf-8', errors='replace', timeout=60)
    if r.returncode: raise RuntimeError(r.stdout + r.stderr)
    return r.stdout.strip()
def shell(command):
    return call('shell', '/sbin/leo-framework-exec /system/bin/sh -c ' + shlex.quote(command))
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('cat /leo-framework-internal') == 'Validated internal Android partitions.'
assert shell('sha256sum /system/vendor/lib/hw/gralloc.default.so').split()[0] != \
    hashlib.sha256((out/'gralloc-adreno-bridge.so').read_bytes()).hexdigest(), \
    'Bridge already active; refusing to overwrite the original backups'
sources = {}
for name in ('libgsl.so','libadreno_utils.so','libsc-a3xx.so'):
    sources['lib/'+name] = here.parent / 'analysis/system/vendor/lib' / name
for name in ('libmemalloc.so','libqdMetaData.so','libqdutils.so','libqservice.so'):
    sources['lib/'+name] = here.parent / 'analysis/system/lib' / name
for source in (here.parent / 'analysis/system/vendor/lib/egl').glob('*.so'):
    sources['lib/leo-adreno/egl/'+source.name] = source  # Kept inactive until switch.
sources['lib/leo-adreno/gralloc.msm8909.so'] = here.parent / 'analysis/system/lib/hw/gralloc.msm8909.so'
sources['lib/leo-adreno/libhwui-adreno.so'] = out / 'libhwui-adreno.so'
for name in ('a300_pfp.fw','a300_pm4.fw'):
    sources['firmware/'+name] = here.parent / 'analysis/system/etc/firmware' / name
hashes = {}
for name, source in sources.items():
    destination = '/system/vendor/' + name
    exists = shell('if [ -e ' + destination + ' ]; then sha256sum ' + destination + '; fi')
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    assert not exists or exists.split()[0] == digest, 'Different existing file: '+destination
    local = stage / name; local.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, local); hashes[destination] = digest
backups = {}
for name in ('gralloc.default.so','hwcomposer.default.so','hwcomposer.leo.so'):
    original = '/system/vendor/lib/hw/' + name
    digest = shell('sha256sum ' + original).split()[0]
    path = out / ('adreno-backup-' + name)
    shell('cp ' + original + ' /tmp/adreno-backup-' + name)
    call('pull','/tmp/adreno-backup-'+name,str(path))
    assert hashlib.sha256(path.read_bytes()).hexdigest() == digest
    backups[original] = digest
    destination = stage / 'lib/leo-adreno' / ('gralloc-software.so' if name=='gralloc.default.so' else name)
    destination.parent.mkdir(exist_ok=True)
    shutil.copy2(path,destination)
    hashes['/system/vendor/lib/leo-adreno/'+destination.name] = digest
original_hwui='/system/lib/libhwui.so'
digest=shell('sha256sum '+original_hwui).split()[0]
shell('cp '+original_hwui+' /tmp/adreno-backup-libhwui.so')
local_hwui=out/'adreno-backup-libhwui.so'
call('pull','/tmp/adreno-backup-libhwui.so',str(local_hwui))
assert hashlib.sha256(local_hwui.read_bytes()).hexdigest()==digest
shutil.copy2(local_hwui,stage/'lib/leo-adreno/libhwui-original.so')
hashes['/system/vendor/lib/leo-adreno/libhwui-original.so']=digest
backups[original_hwui]=digest
shutil.copy2(out/'gralloc-adreno-bridge.so',stage/'lib/leo-adreno/gralloc-adreno-bridge.so')
hwc = Path(r'\\wsl.localhost\kaios\root\android81-leo\src\out\target\product\leo\system\vendor\lib\hw\hwcomposer.leo.so')
shutil.copy2(hwc,stage/'lib/leo-adreno/hwcomposer-adreno.so')
for name in ('gralloc-adreno-bridge.so','hwcomposer-adreno.so'):
    hashes['/system/vendor/lib/leo-adreno/'+name] = hashlib.sha256((stage/'lib/leo-adreno'/name).read_bytes()).hexdigest()
call('push',str(stage),'/tmp/')
shell("set -e; mount -o remount,rw /system; trap 'mount -o remount,ro /system' EXIT; "
      'cp -R /tmp/adreno-integration-stage/. /system/vendor/; '
      'chmod 644 ' + ' '.join(hashes) + '; '
      'restorecon -R /system/vendor/lib/leo-adreno /system/vendor/firmware; sync')
for path, digest in hashes.items():
    assert shell('sha256sum '+path).split()[0] == digest, path
assert ' /system ext4 ro,' in shell('cat /proc/mounts')
(out/'adreno-integration-stage.json').write_text(json.dumps({'files':hashes,'backups':backups},indent=2))
print('Additive GPU files verified; active gralloc/HWC/EGL selection unchanged.')
