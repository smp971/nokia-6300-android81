"""Stage a private vendor library directory; do not replace Android libraries."""
from pathlib import Path
import json
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT/'android81/output'
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
available = subprocess.check_output(ADB+['shell',
    '/sbin/leo-framework-exec /system/bin/ls /system/lib']).decode().split()
available += subprocess.check_output(ADB+['shell',
    '/sbin/leo-framework-exec /system/bin/ls /system/vendor/lib']).decode().split()
graph = json.loads((OUT/'radio-audit/stock-dependencies.json').read_text())
stage = OUT/'radio-probe'
(stage/'lib').mkdir(parents=True, exist_ok=True)
# A previous probe may have used stock libraries also supplied by AOSP vendor.
for name in available:
    (stage/'lib'/name).unlink(missing_ok=True)
copied = []
for relative in graph:
    path = ROOT/'analysis/system'/relative
    if path.suffix == '.so' and path.name not in available:
        shutil.copy2(path, stage/'lib'/path.name)
        copied.append(relative)
shutil.copy2(OUT/'radio-library-probe', stage/'probe')
modern_xml = Path('//wsl.localhost/kaios/root/android81-leo/src/out/target/product/leo/system/lib/libxml2.so')
if modern_xml.exists():
    shutil.copy2(modern_xml, stage/'lib/libxml2.so')
(OUT/'radio-audit/staged-libraries.json').write_text(json.dumps(copied, indent=2))
subprocess.run(ADB+['shell', '/sbin/leo-framework-exec /system/bin/mkdir -p '
                   '/tmp/leo-radio-probe/lib'], check=True)
subprocess.run(ADB+['push', *map(str, sorted((stage/'lib').glob('*.so'))),
                   '/tmp/leo-radio-probe/lib/'], check=True)
subprocess.run(ADB+['push', str(stage/'probe'), '/tmp/leo-radio-probe/probe'], check=True)
for name in ('libril.so', 'librilutils.so'):
    subprocess.run(ADB+['shell', '/sbin/leo-framework-exec /system/bin/rm -f '
                       '/tmp/leo-radio-probe/lib/'+name], check=True)
subprocess.run(ADB+['shell', '/sbin/leo-framework-exec /system/bin/chmod 755 /tmp/leo-radio-probe/probe'], check=True)
result = subprocess.run(ADB+['shell',
    '/sbin/leo-framework-exec /system/bin/env LD_LIBRARY_PATH=/tmp/leo-radio-probe/lib:/system/vendor/lib '
    '/tmp/leo-radio-probe/probe libqmi_csi.so libqmi.so libril-qc-qmi-1.so'],
    capture_output=True, timeout=30)
(OUT/'radio-audit/linker-probe.txt').write_bytes(result.stdout+result.stderr)
print(result.stdout.decode(errors='replace'))
print(result.stderr.decode(errors='replace'))
raise SystemExit(result.returncode)
