"""Enable/select the physical-key IME on 695d, preserving the old selection locally."""
from pathlib import Path
import json
import shlex
import subprocess

ROOT = Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*args):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*args])],timeout=45).decode('utf-8',errors='replace')
def java(*args):
    return subprocess.check_output(['python', str(ROOT/'framework-command.py'), '/system/bin/sh', *args], timeout=60).decode('utf-8', errors='replace')

backup=ROOT/'output/keypad-ime-before.json'
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
if not backup.exists():
    backup.write_text(json.dumps(dict(
        default=java('/system/bin/settings','get','secure','default_input_method').strip(),
        enabled=java('/system/bin/settings','get','secure','enabled_input_methods').strip()
    ),indent=2),encoding='utf-8')
print(java('/system/bin/ime','enable','ro.leo.keypad/.KeypadIme').strip())
print(java('/system/bin/ime','set','ro.leo.keypad/.KeypadIme').strip())
assert java('/system/bin/settings','get','secure','default_input_method').strip() in (
    'ro.leo.keypad/.KeypadIme','ro.leo.keypad/ro.leo.keypad.KeypadIme')
marker='# Default physical-key text input, only when the IME is installed.'
script=native('/system/bin/cat','/system/etc/leo-first-setup.sh').replace('\r\n','\n')
if marker not in script:
    before=ROOT/'output/keypad-ime-startup-before.sh'
    if not before.exists(): before.write_bytes(script.encode('utf-8'))
    block=(ROOT/'leo-first-setup.sh').read_text().split(marker,1)[1]
    staged=ROOT/'output/keypad-ime-startup.sh'
    staged.write_bytes((script.rstrip()+'\n'+marker+block).encode('utf-8'))
    subprocess.run(ADB+['push',str(staged),'/tmp/leo-keypad-startup.sh'],check=True)
    native('/system/bin/mount','-o','remount,rw','/system')
    try:
        path='/system/etc/leo-first-setup.sh'
        native('/system/bin/cp','/tmp/leo-keypad-startup.sh',path+'.new')
        native('/system/bin/chmod','644',path+'.new')
        native('/system/bin/chown','0:0',path+'.new')
        native('/system/bin/mv',path+'.new',path)
        native('/system/bin/restorecon',path)
    finally: native('/system/bin/mount','-o','remount,ro','/system')
print('Leo Multi-tap selected; previous IME settings saved in ignored output/')
