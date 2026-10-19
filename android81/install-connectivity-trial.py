"""Install disabled connectivity trial services; no boot/recovery or persist writes."""
from pathlib import Path
import hashlib
import json
import re
import shlex
import subprocess

root = Path(__file__).resolve().parent
stage = root / 'output/connectivity-hal-stage'
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']

def shell(command):
    return subprocess.check_output(adb + ['shell', '/sbin/leo-framework-exec /system/bin/sh -c '
                                         + shlex.quote(command)], timeout=30).decode().strip()

assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert set(re.findall(r'\bmCallState=(\d+)', shell('dumpsys telephony.registry'))) == {'0'}
files = {'vendor/leo-connectivity/' + name: stage / name
         for name in json.loads((stage / 'manifest.json').read_text()) if not name.startswith('hw/')}
for name in ['libbt-vendor.so', 'libbtnv.so']:
    files['vendor/leo-connectivity/lib/' + name] = root.parent / 'analysis/system/vendor/lib' / name
files['vendor/leo-connectivity/modules/pronto_wlan.ko'] = root.parent / 'analysis/system/lib/modules/pronto/pronto_wlan.ko'
files['vendor/leo-connectivity/start.sh'] = root / 'connectivity-start.sh'
files['vendor/leo-connectivity/no-cal.sh'] = root / 'wcnss-no-cal-trial.sh'
files['etc/init/init.leo.connectivity.rc'] = root / 'init.leo.connectivity.rc'
files['etc/permissions/leo-connectivity.xml'] = root / 'device/nokia/leo/leo-connectivity.xml'
files['etc/wifi/wpa_supplicant.conf'] = root / 'device/nokia/leo/wpa_supplicant.conf'
expected = {}
previous_file = root / 'output/connectivity-trial-installed.json'
previous = json.loads(previous_file.read_text()) if previous_file.exists() else {}
pending = []
for relative, source in files.items():
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    expected[relative] = digest
    destination = '/system/' + relative
    if shell('test -e ' + destination + ' && echo present || true'):
        current = shell('sha256sum ' + destination).split()[0]
        assert current == digest or current == previous.get(relative), destination
        if current == digest:
            continue
    pending.append(relative)
    temp = '/tmp/leo-connectivity-install/' + relative
    shell('mkdir -p ' + temp.rsplit('/', 1)[0])
    subprocess.run(adb + ['push', str(source), temp], check=True, capture_output=True)
    assert shell('sha256sum ' + temp).split()[0] == digest
shell('mount -o remount,rw /system')
try:
    for relative in pending:
        destination = '/system/' + relative
        shell('mkdir -p ' + destination.rsplit('/', 1)[0])
        shell('cp /tmp/leo-connectivity-install/' + relative + ' ' + destination + '.new')
        mode = '755' if '/bin/' in relative or relative.endswith('.sh') else '644'
        shell('chmod ' + mode + ' ' + destination + '.new')
        shell('mv ' + destination + '.new ' + destination)
        shell('restorecon ' + destination)
finally:
    shell('mount -o remount,ro /system')
for relative, digest in expected.items():
    assert shell('sha256sum /system/' + relative).split()[0] == digest
(root / 'output/connectivity-trial-installed.json').write_text(json.dumps(expected, indent=2))
print('Installed disabled trial services and feature declarations; system read-only; reboot required')
