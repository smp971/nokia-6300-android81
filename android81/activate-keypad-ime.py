"""Enable/select the physical-key IME on 695d, preserving the old selection locally."""
from pathlib import Path
import json
import subprocess

ROOT = Path(__file__).resolve().parent
def java(*args):
    return subprocess.check_output(['python', str(ROOT/'framework-command.py'), '/system/bin/sh', *args], timeout=60).decode('utf-8', errors='replace')

backup=ROOT/'output/keypad-ime-before.json'
if not backup.exists():
    backup.write_text(json.dumps(dict(
        default=java('/system/bin/settings','get','secure','default_input_method').strip(),
        enabled=java('/system/bin/settings','get','secure','enabled_input_methods').strip()
    ),indent=2),encoding='utf-8')
print(java('/system/bin/ime','enable','ro.leo.keypad/.KeypadIme').strip())
print(java('/system/bin/ime','set','ro.leo.keypad/.KeypadIme').strip())
assert java('/system/bin/settings','get','secure','default_input_method').strip() in (
    'ro.leo.keypad/.KeypadIme','ro.leo.keypad/ro.leo.keypad.KeypadIme')
print('Leo Multi-tap selected; previous IME settings saved in ignored output/')
