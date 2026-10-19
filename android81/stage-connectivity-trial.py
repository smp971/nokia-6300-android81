"""Stage original WCNSS components for bounded trials on 695d; no boot activation."""
from pathlib import Path
import hashlib
import json
import shlex
import subprocess

root = Path(__file__).resolve().parent
stock = root.parent / 'analysis/system'
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']

def shell(command):
    return subprocess.check_output(adb + ['shell', '/sbin/leo-framework-exec /system/bin/sh -c '
                                         + shlex.quote(command)], timeout=45).decode().strip()

assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('uname -r') == '4.9.240-perf-g4f9e270fd77f'
states = shell('dumpsys telephony.registry')
import re
assert set(re.findall(r'\bmCallState=(\d+)', states)) == {'0'}, 'A call is active'
private = root / 'output/connectivity-private'
private.mkdir(parents=True, exist_ok=True)
nv = private / 'WCNSS_qcom_wlan_nv.bin'
if not nv.exists():
    shell('mkdir -p /data/local/tmp/leo-persist-ro')
    shell('mount -t ext4 -o ro /dev/block/bootdevice/by-name/persist /data/local/tmp/leo-persist-ro')
    try:
        shell('cp /data/local/tmp/leo-persist-ro/WCNSS_qcom_wlan_nv.bin /tmp/leo-wlan-nv.bin')
        subprocess.run(adb + ['pull', '/tmp/leo-wlan-nv.bin', str(nv)], check=True, capture_output=True)
    finally:
        shell('umount /data/local/tmp/leo-persist-ro')
assert nv.stat().st_size == 31723
files = {
    'bin/wcnss_service': stock / 'bin/wcnss_service',
    'lib/libqcci_legacy.so': stock / 'vendor/lib/libqcci_legacy.so',
    'lib/libidl.so': stock / 'vendor/lib/libidl.so',
    'lib/libbt-vendor.so': stock / 'vendor/lib/libbt-vendor.so',
    'lib/libbtnv.so': stock / 'vendor/lib/libbtnv.so',
    'modules/pronto_wlan.ko': stock / 'lib/modules/pronto/pronto_wlan.ko',
}
for name in ['WCNSS_qcom_cfg.ini', 'WCNSS_qcom_wlan_nv.bin', 'WCNSS_cfg.dat']:
    files['firmware/' + name] = stock / 'etc/firmware/wlan/prima' / name
files['firmware/WCNSS_qcom_cfg.ini'] = stock / 'etc/wifi/WCNSS_qcom_cfg.ini'
files['firmware/WCNSS_qcom_wlan_nv.bin'] = nv
files['firmware/wifi-default.ini'] = stock / 'etc/wifi/WCNSS_qcom_cfg.ini'
report = {}
for relative, source in files.items():
    assert source.is_file() and source.stat().st_size > 0, str(source)
    destination = '/tmp/leo-connectivity/' + relative
    shell('mkdir -p ' + shlex.quote(str(Path(destination).parent).replace('\\', '/')))
    subprocess.run(adb + ['push', str(source), destination], check=True, capture_output=True)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    assert shell('sha256sum ' + destination).split()[0] == digest
    report[relative] = digest
shell('chmod 755 /tmp/leo-connectivity/bin/wcnss_service')
# ueventd must see these firmware paths in its own mount namespace too.
destinations = {name: '/system/vendor/firmware/wlan/prima/' + name
                for name in ['WCNSS_qcom_cfg.ini', 'WCNSS_qcom_wlan_nv.bin', 'WCNSS_cfg.dat']}
destinations['wifi-default.ini'] = '/system/etc/wifi/WCNSS_qcom_cfg.ini'
shell('mount -o remount,rw /system')
try:
    for name, destination in destinations.items():
        source = '/tmp/leo-connectivity/firmware/' + name
        if shell('test -e ' + destination + ' && echo present || true'):
            assert shell('sha256sum ' + destination).split()[0] == report['firmware/' + name]
            continue
        directory = destination.rsplit('/', 1)[0]
        shell('mkdir -p ' + directory)
        shell('cp ' + source + ' ' + destination + '.new')
        shell('chmod 644 ' + destination + '.new')
        shell('mv ' + destination + '.new ' + destination)
        shell('restorecon ' + destination)
finally:
    shell('mount -o remount,ro /system')
(root / 'output/connectivity-trial-staged.json').write_text(json.dumps(report, indent=2))
print('Staged and hash-verified WCNSS trial components; system read-only; no services activated')
