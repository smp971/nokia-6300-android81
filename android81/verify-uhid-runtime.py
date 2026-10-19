"""Read-only confirmation of the rebuilt kernel and MX Master HID connection."""
from pathlib import Path
import json
import re
import shlex
import subprocess

out = Path(__file__).resolve().parent / 'output/kernel-uhid'
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
def native(*args):
    return subprocess.check_output(adb + ['shell', shlex.join(['/sbin/leo-framework-exec', *args])],
                                   timeout=25).decode(errors='replace').replace('\r', '').strip()

assert 'androidboot.serialno=695d ' in native('/system/bin/cat', '/proc/cmdline')
assert native('/system/bin/getprop', 'sys.boot_completed') == '1'
expected = json.loads((out / 'module-compatibility.json').read_text())['kernel_version']
assert native('/system/bin/cat', '/proc/version') == expected
assert native('/system/bin/stat', '-c', '%a:%U:%G', '/dev/uhid') == '660:uhid:uhid'
devices = native('/system/bin/cat', '/proc/bus/input/devices')
mouse = next((block for block in devices.split('\n\n') if 'Name="MX Master 3 Mac"' in block), '')
registered = bool('/devices/virtual/misc/uhid/' in mouse and
                  re.search(r'Handlers=.*mouse\d+.*event\d+', mouse))
bluetooth = native('/system/bin/dumpsys', 'bluetooth_manager')
hid = bluetooth.split('Profile: HidService', 1)[-1].split('Profile:', 1)[0]
address = re.search(r'^U: Uniq=(.+)$', mouse, re.M)
connected = bool(address and re.search(re.escape(address.group(1)) + r'\s*:\s*2\b', hid, re.I))
report = {'boot_id': native('/system/bin/cat', '/proc/sys/kernel/random/boot_id'),
          'rebuilt_kernel_running': True, 'uhid_node_permissions_correct': True,
          'mx_master_hid_connected': connected, 'mouse_input_device_created': registered,
          'audio_autoload': native('/system/bin/getprop', 'persist.leo.audio.autoload'),
          'system_read_only': ' /system ext4 ro,' in native('/system/bin/cat', '/proc/mounts'),
          'physical_input_confirmation': 'Recorded separately; this script checks software state only.'}
(out / 'uhid-runtime-verified.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
raise SystemExit(0 if connected and registered else 1)
