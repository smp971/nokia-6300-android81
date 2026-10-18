"""Install the Dialer update and keypad map on 695d only, without rebooting."""
from pathlib import Path
import hashlib
import re
import shlex
import subprocess

here = Path(__file__).resolve().parent
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']

def native(*args):
    return subprocess.check_output(adb + ['shell', shlex.join(
        ['/sbin/leo-framework-exec', *args])], text=True, timeout=45)

def java(*args):
    return subprocess.check_output(['python', str(here / 'framework-command.py'),
                                    '/system/bin/sh', *args], text=True, timeout=120)

telecom = native('/system/bin/dumpsys', 'telecom')
calls = re.search(r'^  mCalls:[ \t]*([^\r\n]*)', telecom, re.M)
assert calls and not calls.group(1).strip(), 'An active Telecom call prevents installation'
states = re.findall(r'\bmCallState=(\d+)', native('/system/bin/dumpsys', 'telephony.registry'))
assert len(states) >= 2 and all(state == '0' for state in states), 'Both SIMs must be idle'
apk = here / 'output/leo-keypad-dialer.apk'
assert apk.is_file()
backup = here / 'output/call-keypad-backup'
backup.mkdir(parents=True, exist_ok=True)
for name, source in [('Dialer.apk', '/system/priv-app/Dialer/Dialer.apk'),
                     ('matrix_keypad.kl', '/system/usr/keylayout/matrix_keypad.kl')]:
    local = backup / name
    if not local.exists():
        native('/system/bin/cp', source, '/tmp/leo-before-' + name)
        subprocess.run(adb + ['pull', '/tmp/leo-before-' + name, str(local)], check=True)
        (backup / (name + '.sha256')).write_text(hashlib.sha256(local.read_bytes()).hexdigest() + '\n')
old = backup / 'end-button-behavior.txt'
if not old.exists():
    old.write_text(java('/system/bin/settings', 'get', 'system', 'end_button_behavior').strip() + '\n')
subprocess.run(adb + ['push', str(apk), '/tmp/leo-keypad-dialer.apk'], check=True)
result = java('/system/bin/pm', 'install', '-r', '/tmp/leo-keypad-dialer.apk')
assert 'Success' in result, result
print('Dialer update installed, existing app data retained')
java('/system/bin/settings', 'put', 'system', 'end_button_behavior', '1')
layout = here / 'device/nokia/leo/matrix_keypad.kl'
subprocess.run(adb + ['push', str(layout), '/tmp/leo-matrix-keypad.kl'], check=True)
native('/system/bin/mount', '-o', 'remount,rw', '/system')
try:
    destination = '/system/usr/keylayout/matrix_keypad.kl'
    native('/system/bin/cp', '/tmp/leo-matrix-keypad.kl', destination + '.new')
    native('/system/bin/chmod', '644', destination + '.new')
    native('/system/bin/chown', '0:0', destination + '.new')
    native('/system/bin/mv', destination + '.new', destination)
    native('/system/bin/restorecon', destination)
finally:
    native('/system/bin/mount', '-o', 'remount,ro', '/system')
assert java('/system/bin/settings', 'get', 'system', 'end_button_behavior').strip() == '1'
assert 'ENDCALL' in native('/system/bin/cat', '/system/usr/keylayout/matrix_keypad.kl')
print('Keypad map installed; reboot required to reload physical key codes')
