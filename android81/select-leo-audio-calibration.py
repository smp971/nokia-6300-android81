"""Select the stock Leo ACDB files; preserve properties for an explicit rollback.

Default is read-only. --apply and --rollback restart Android audio, so calls
must be idle. This does not load kernel modules or change mixer gains.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parent
FW = ['python', '-X', 'utf8', str(ROOT / 'framework-command.py')]
STOCK = ROOT.parent / 'analysis/system/etc/acdbdata/MTP/Leo'
NAMES = ['Bluetooth', 'General', 'Global', 'Handset', 'Hdmi', 'Headset', 'Speaker']
EXPECTED = {f'persist.audio.calfile{i}':
            f'/system/etc/acdbdata/MTP/Leo/Leo_{name}_cal.acdb'
            for i, name in enumerate(NAMES)}


def run(*args):
    return subprocess.check_output(FW + list(args), timeout=35).decode('utf-8', errors='replace').strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--rollback', type=Path, help='before.json written by a previous --apply')
    args = parser.parse_args()
    assert 'androidboot.serialno=695d' in run('/system/bin/cat', '/proc/cmdline')
    assert run('/system/bin/cat', '/leo-framework-internal') == 'Validated internal Android partitions.'
    raw = run('/system/bin/getprop').replace('\r\n', '\n')
    props = dict(re.findall(r'^\[([^]]+)\]: \[(.*)\]$', raw, re.M))
    keys = [f'persist.audio.calfile{i}' for i in range(20)]
    before = {key: props.get(key, '') for key in keys}
    if not args.apply and not args.rollback:
        print(json.dumps({'current': before, 'proposed': EXPECTED}, indent=2))
        return
    states = re.findall(r'^\s*mCallState=(\d+)', run('/system/bin/dumpsys', 'telephony.registry'), re.M)
    assert len(states) == 2 and set(states) == {'0'}, 'Finish calls before restarting audio'
    assert props.get('sys.leo.audio.state') == 'active', 'Stock audio must already be active'
    if args.rollback:
        target = json.loads(args.rollback.read_text(encoding='utf-8'))
        assert set(target) == set(keys) and all(isinstance(v, str) for v in target.values())
    else:
        for path in EXPECTED.values():
            digest = hashlib.sha256((STOCK / Path(path).name).read_bytes()).hexdigest()
            assert run('/system/bin/sha256sum', path).split()[0] == digest, path
        target = {key: EXPECTED.get(key, '') for key in keys}
    backup = ROOT / 'output/audio-calibration' / time.strftime('%Y%m%d-%H%M%S')
    backup.mkdir(parents=True, exist_ok=False)
    (backup / 'before.json').write_text(json.dumps(before, indent=2), encoding='utf-8')
    (backup / 'target.json').write_text(json.dumps(target, indent=2), encoding='utf-8')
    # Persisted properties are loaded before audio HAL starts on later boots.
    # The original loader consumes contiguous calfile0..N and stops at empty.
    commands = ['set -eu']
    commands += ['setprop ' + key + ' ' + shlex.quote(target[key]) for key in keys]
    run('/system/bin/sh', '-c', '\n'.join(commands))
    for key in keys:
        assert run('/system/bin/getprop', key) == target[key], key
    print('Saved rollback properties: ' + str(backup / 'before.json'), flush=True)
    # No module reload, modem restart, global product change or gain adjustment.
    run('/system/bin/sh', '-c', 'setprop ctl.stop audioserver; setprop ctl.stop audio-hal-2-0; '
        'sleep 1; setprop ctl.start audio-hal-2-0; sleep 1; setprop ctl.start audioserver')
    print('Audio restart requested. Read initialization logs before accepting call quality.', flush=True)


if __name__ == '__main__':
    main()
