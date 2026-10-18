"""Select Leo Visual HOME and its opt-in accessibility pointer on 695d."""
from pathlib import Path
import json
import shlex
import subprocess

ROOT = Path(__file__).resolve().parent
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']

def native(*args):
    return subprocess.check_output(ADB+['shell', shlex.join(['/sbin/leo-framework-exec', *args])], timeout=45).decode('utf-8', errors='replace')

def java(*args):
    return subprocess.check_output(['python', str(ROOT/'framework-command.py'), '/system/bin/sh', *args], timeout=45).decode('utf-8', errors='replace')

assert 'androidboot.serialno=695d' in native('/system/bin/cat', '/proc/cmdline')
assert 'package:' in java('/system/bin/pm', 'path', 'ro.leo.visual')
backup = ROOT/'output/visual-home-before.json'
old_script = native('/system/bin/cat', '/system/etc/leo-first-setup.sh').replace('\r\n', '\n')
if not backup.exists():
    backup.write_text(json.dumps(dict(
        home=native('/system/bin/cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'),
        services=java('/system/bin/settings', 'get', 'secure', 'enabled_accessibility_services').strip(),
        accessibility=java('/system/bin/settings', 'get', 'secure', 'accessibility_enabled').strip(),
        setup=old_script), indent=2), encoding='utf-8')
marker = '# Use Leo Visual when installed; otherwise let Android resolve an available HOME.'
if marker not in old_script:
    needle = 'cmd package set-home-activity --user 0 ro.leo.shell/.MainActivity\ncmd activity start-activity -n ro.leo.shell/.MainActivity'
    previous_marker = '# Respect the selected shell on subsequent boots; classic Leo is the fallback.'
    replacement = marker + (ROOT/'leo-first-setup.sh').read_text().split(marker, 1)[1]
    if previous_marker in old_script:
        updated = old_script.split(previous_marker,1)[0] + replacement
    else:
        assert needle in old_script
        updated = old_script.replace(needle, replacement.rstrip())
    staged = ROOT/'output/visual-home-setup.sh'
    staged.write_bytes(updated.encode('utf-8'))
    subprocess.run(ADB+['push', str(staged), '/tmp/leo-visual-setup.sh'], check=True)
    native('/system/bin/mount', '-o', 'remount,rw', '/system')
    try:
        dest='/system/etc/leo-first-setup.sh'
        native('/system/bin/cp', '/tmp/leo-visual-setup.sh', dest+'.new')
        native('/system/bin/chmod', '644', dest+'.new')
        native('/system/bin/chown', '0:0', dest+'.new')
        native('/system/bin/mv', dest+'.new', dest)
        native('/system/bin/restorecon', dest)
    finally:
        native('/system/bin/mount', '-o', 'remount,ro', '/system')
java('/system/bin/pm', 'grant', 'ro.leo.visual', 'android.permission.READ_PHONE_STATE')
services=java('/system/bin/settings', 'get', 'secure', 'enabled_accessibility_services').strip()
enabled=[] if services in ('', 'null') else services.split(':')
component='ro.leo.visual/ro.leo.visual.LeoPointerService'
if component in enabled:
    # Rebind our service after APK replacement; preserve every other service.
    others=[entry for entry in enabled if entry != component]
    java('/system/bin/settings', 'put', 'secure', 'enabled_accessibility_services', ':'.join(others))
if component not in enabled:
    enabled.append(component)
java('/system/bin/settings', 'put', 'secure', 'enabled_accessibility_services', ':'.join(enabled))
java('/system/bin/settings', 'put', 'secure', 'accessibility_enabled', '1')
native('/system/bin/setprop', 'persist.leo.home', 'visual')
print(native('/system/bin/cmd', 'package', 'set-home-activity', '--user', '0', 'ro.leo.visual/.MainActivity').strip())
java('/system/bin/input', 'keyevent', '224', '3')
assert 'ro.leo.visual' in native('/system/bin/cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
print('Leo Visual is HOME; pointer service enabled, mouse remains off until selected')
