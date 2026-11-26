"""Explicit, bounded live keySIP test for the owner's test extension 200.

Uses the existing account without exporting credentials. No audio is recorded.
The production APK must already contain the sanitized SipService dump hook.
Instrumentation restarts keySIP, so the host checks for calls BEFORE starting it.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, choices=['695d'])
parser.add_argument('--allow-live', action='store_true', help='authorize one real call')
parser.add_argument('--destination', required=True, choices=['200'])
args = parser.parse_args()
if not args.allow_live:
    parser.error('No call made: --allow-live is required')


def android(command, timeout=25):
    result = subprocess.run(
        [sys.executable, str(ROOT/'framework-command.py'), '--timeout', str(timeout),
         '/system/bin/sh', '-c', command], capture_output=True, text=True,
        encoding='utf-8', errors='replace', timeout=timeout+10)
    if result.returncode:
        raise RuntimeError('Android diagnostic failed; no retry attempted')
    return result.stdout


def idle_preflight():
    # Never print telephony.registry: it can include private subscriber details.
    registry = android('dumpsys telephony.registry')
    states = re.findall(r'^\s*mCallState=(\d+)\s*$', registry, re.M)
    if not states or any(state != '0' for state in states):
        raise RuntimeError('Cellular state is busy or unavailable; left untouched')
    sip = android('dumpsys activity service ro.n0d3.kaisip/.SipService')
    if 'No services match:' in sip:
        return
    state = re.search(r'keySIP call_active=(true|false)\b', sip)
    if not state or state.group(1) != 'false':
        raise RuntimeError('SIP call is active or state is unavailable; left untouched')


idle_preflight()
print('PASS: no active cellular or SIP call before test setup', flush=True)
subprocess.run([sys.executable, str(ROOT/'build-kaisip-tests-windows.py'), '--live'],
               check=True, stdout=subprocess.DEVNULL)
apk = ROOT/'output/kaisip-checks/tests.apk'
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', args.serial]
subprocess.run(adb+['push', str(apk), '/proc/1/root/data/local/tmp/keysip-live-checks.apk'],
               check=True, capture_output=True)
android('pm install -r /data/local/tmp/keysip-live-checks.apk')
idle_preflight()
print('Running one keypad call to test extension 200 (maximum 25 seconds ringing)...', flush=True)
try:
    output = android('am instrument -w -e allow_live true -e destination 200 '
                     'ro.n0d3.kaisip.checks/ro.n0d3.kaisip.KeySipLiveChecks', 100)
    # Only publish the runner's allowlisted report, never arbitrary device logs.
    match = re.search(r'^INSTRUMENTATION_RESULT: diagnostics=(\{.*\})\s*$', output, re.M)
    if not match:
        raise RuntimeError('Runner returned no sanitized report; inspect locally')
    report = json.loads(match.group(1))
    target = ROOT/'output/keysip-call-fix/keypad-live-result.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    rtp = report.get('rtp', {})
    passed = (report.get('state') == 'incall' and
              report.get('dial_control') == 'keypad-2-0-0-Call' and
              report.get('speaker_switch') is True and
              report.get('earpiece_switch') is True and
              rtp.get('packetsSent', 0) > 0 and rtp.get('packetsReceived', 0) > 0 and
              'INSTRUMENTATION_RESULT: error=' not in output)
    print(json.dumps(report, indent=2))
    if not passed:
        raise RuntimeError('Call check incomplete; report saved without automatic retry')
    print('PASS: keypad dial, connection, bidirectional RTP and audio routes. Voice audibility remains unmeasured.')
finally:
    # Remove only this disposable runner, never production data or the SIP account.
    android('pm uninstall ro.n0d3.kaisip.checks; rm -f /data/local/tmp/keysip-live-checks.apk')
