"""Verify new IME settings with Android key events in the private test editor.

Uses a real editor/InputConnection; no transmission or text logging. Restore the two settings after the probe.
"""
from pathlib import Path
import subprocess
import sys
import time
import json

root=Path(__file__).resolve().parent
def call(*args):
    return subprocess.check_output([sys.executable,'-X','utf8',str(root/'framework-command.py'),*args],timeout=35,encoding='utf-8',errors='replace').strip()
def java(*args):return call('/system/bin/sh',*args)
keys=('leo_keypad_default_mode','leo_keypad_multitap_ms')
before={key:java('/system/bin/settings','get','secure',key) for key in keys}
(root/'output/settings-input-before.json').write_text(json.dumps(before,indent=2),encoding='utf-8')
try:
    java('/system/bin/settings','put','secure',keys[0],'1')
    java('/system/bin/settings','put','secure',keys[1],'500')
    output=call('--timeout','60','/system/bin/sh','/system/bin/am','instrument','-w','ro.leo.visual.tests/ro.leo.keypad.ImeSettingsTests')
    print(output)
    assert 'result=PASS' in output and 'INSTRUMENTATION_CODE: -1' in output,'IME integration failed'
finally:
    for key,value in before.items():
        java('/system/bin/settings','delete' if value=='null' else 'put','secure',key,*(() if value=='null' else (value,)))
