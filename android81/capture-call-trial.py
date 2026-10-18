"""Observe an incoming-call trial on 695d; never answers, rejects or places calls."""
from pathlib import Path
import argparse
import json
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--seconds', type=int, default=600)
args = parser.parse_args()
assert 10 <= args.seconds <= 600
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
out = Path(__file__).resolve().parent / 'output/call-fullscreen-trial'
out.mkdir(parents=True, exist_ok=True)

def native(*command):
    return subprocess.check_output(adb + ['shell', '/sbin/leo-framework-exec', *command],
                                   timeout=8).decode('utf-8', errors='replace')

assert 'androidboot.serialno=695d' in native('/system/bin/cat', '/proc/cmdline')
previous = None
start = time.monotonic()
with (out / 'private-logcat.log').open('wb') as log, (out / 'states.jsonl').open('w') as states:
    capture = subprocess.Popen(adb + ['logcat', '-v', 'threadtime', '-T', '1'],
                               stdout=log, stderr=subprocess.STDOUT)
    print('Incoming-call observation started; no automatic call actions', flush=True)
    try:
        while time.monotonic() - start < args.seconds:
            try:
                registry = native('/system/bin/dumpsys', 'telephony.registry')
                window = native('/system/bin/dumpsys', 'window', 'windows')
                focus = re.search(r'mCurrentFocus=([^\r\n]+)', window)
                report = {
                    'call_states': re.findall(r'\bmCallState=(\d+)', registry),
                    'real_call_ui_focused': bool(focus and 'InCallActivity' in focus.group(1)),
                }
            except (subprocess.SubprocessError, OSError):
                report = {'device_unavailable': True}
            if report != previous:
                previous = report.copy()
                report['elapsed_seconds'] = round(time.monotonic() - start)
                line = json.dumps(report)
                print(line, flush=True)
                states.write(line + '\n')
                states.flush()
            time.sleep(2)
    finally:
        capture.terminate()
        capture.wait(timeout=10)
print('Observation finished; detailed logs remain in ignored local output/', flush=True)
