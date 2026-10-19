"""Check current Wi-Fi/Bluetooth software state; optional interface-bound ping."""
from pathlib import Path
import argparse
import json
import re
import shlex
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--ssid', default='')
parser.add_argument('--bluetooth-target', default='')
parser.add_argument('--ping', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']

def native(*command):
    return subprocess.check_output(adb + ['shell', shlex.join(['/sbin/leo-framework-exec', *command])],
                                   timeout=25).decode('utf-8', errors='replace').replace('\r', '').strip()

assert 'androidboot.serialno=695d' in native('/system/bin/cat', '/proc/cmdline')
wifi = native('/system/bin/dumpsys', 'wifi')
bluetooth = native('/system/bin/dumpsys', 'bluetooth_manager')
connectivity = native('/system/bin/dumpsys', 'connectivity')
tether = connectivity.split('Tethering:',1)[-1].split('  Log:',1)[0]
processes = native('/system/bin/ps', '-A')
try:
    status = native('/system/bin/env', 'LD_LIBRARY_PATH=/vendor/leo-connectivity/lib:/vendor/lib:/system/lib',
                    '/vendor/leo-connectivity/bin/wpa_cli', '-p', '/data/misc/wifi/sockets', '-i', 'wlan0', 'status')
except subprocess.CalledProcessError:
    status = ''  # Supplicant is legitimately absent when Wi-Fi is off or AP mode is active.
values = dict(line.split('=', 1) for line in status.splitlines() if '=' in line)
bonded = re.search(r'Bonded devices:(.*?)(?:\n\n|$)', bluetooth, re.S | re.I)
report = {
    'boot_id': native('/system/bin/cat', '/proc/sys/kernel/random/boot_id'),
    'setup_state': native('/system/bin/getprop', 'sys.leo.connectivity.state'),
    'wifi_enabled': wifi.startswith('Wi-Fi is enabled'),
    'wifi_station_state': values.get('wpa_state', ''),
    'wifi_address_acquired': bool(values.get('ip_address')),
    'requested_ssid_matches': values.get('ssid') == args.ssid if args.ssid else None,
    'bluetooth_enabled': bool(re.search(r'^\s*enabled: true$', bluetooth, re.M)),
    'bluetooth_state': (re.search(r'^\s*state: (\w+)', bluetooth, re.M) or ['', 'UNKNOWN'])[1],
    'requested_bluetooth_target_bonded': bool(bonded and args.bluetooth_target in bonded.group(1))
        if args.bluetooth_target else None,
    'system_read_only': ' /system ext4 ro,' in native('/system/bin/cat', '/proc/mounts'),
    'automatic_boot_activation': native('/system/bin/getprop', 'persist.leo.connectivity.auto') == '1',
    'boot_sequence_validated': native('/system/bin/getprop', 'persist.leo.connectivity.validated') == '1',
    'hotspot_service_running': native('/system/bin/getprop', 'init.svc.hostapd') == 'running',
    'hotspot_dhcp_running': bool(re.search(r'\bdnsmasq\s*$', processes, re.M)),
    'hotspot_tethered_without_error': bool(re.search(r'wlan0 - TetheredState - lastError = 0',tether)),
    'hotspot_mobile_upstream': bool(re.search(r'Current upstream interface: rmnet_data\d+',tether)),
}
if args.ping:
    ping = subprocess.run(adb + ['shell', '/sbin/leo-framework-exec', '/system/bin/ping',
                                 '-I', 'wlan0', '-c', '2', '-W', '3', '1.1.1.1'],
                          capture_output=True, timeout=15).stdout.decode(errors='replace')
    received = re.search(r'(\d+) (?:packets )?received', ping)
    report['wifi_ping_sent'] = 2
    report['wifi_ping_received'] = int(received.group(1)) if received else 0
(root / 'output/connectivity-trial-verified.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
