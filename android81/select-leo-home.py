"""Select the installed Leo HOME and disable the retained stock launcher.

Reversible with: pm enable --user 0 com.android.launcher3
No APK removal, data deletion, framework restart or partition flashing.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent

def run(*args):
    return subprocess.check_output(
        ['python', '-X', 'utf8', str(ROOT/'framework-command.py'), *args],
        timeout=35, encoding='utf-8', errors='replace')

assert 'androidboot.serialno=695d' in run('/system/bin/cat', '/proc/cmdline')
assert 'package:' in run('/system/bin/sh', '/system/bin/pm', 'path', 'ro.leo.visual')
print(run('/system/bin/cmd', 'package', 'set-home-activity', '--user', '0',
          'ro.leo.visual/.MainActivity').strip())
resolved = run('/system/bin/cmd', 'package', 'resolve-activity', '--brief',
               '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
assert 'ro.leo.visual/.MainActivity' in resolved
print(run('/system/bin/sh', '/system/bin/am', 'start', '-a',
          'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').strip())
print(run('/system/bin/sh', '/system/bin/pm', 'disable-user', '--user', '0',
          'com.android.launcher3').strip())
assert 'com.android.launcher3' in run('/system/bin/sh', '/system/bin/pm',
                                    'list', 'packages', '-d', 'com.android.launcher3')
print('Leo is HOME; stock Launcher3 retained but disabled for user 0.')
