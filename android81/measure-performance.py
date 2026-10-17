"""Private, serial-pinned diagnostics and cold-process activity launch timings.

No settings are changed. --launch stops only the selected application before each
trial; filesystem caches remain warm. Reports can contain private device data.
"""
import argparse
import json
from pathlib import Path
import shlex
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--label', default='snapshot')
parser.add_argument('--launch', help='Explicit package/activity component')
parser.add_argument('--trials', type=int, default=3)
args = parser.parse_args()
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d', 'shell']

def run(*command):
    result = subprocess.run(adb + [shlex.join([
        '/sbin/leo-framework-exec', '/system/bin/' + command[0], *command[1:]])],
        capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.replace('\r', '')

assert 'androidboot.serialno=695d' in run('cat', '/proc/cmdline')
report = {'label': args.label, 'launch': args.launch, 'trials': []}
for name, command in {
    'memory': ['cat', '/proc/meminfo'],
    'swap': ['cat', '/proc/swaps'],
    'zram': ['cat', '/sys/block/zram0/mm_stat'],
    'mounts': ['cat', '/proc/mounts'],
    'properties': ['getprop'],
    'cpu': ['sh', '-c', 'cat /sys/devices/system/cpu/online; '
            'cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_*freq; '
            'cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor'],
    'surfaceflinger': ['dumpsys', 'SurfaceFlinger'],
}.items():
    report[name] = run(*command)
if args.launch:
    assert 1 <= args.trials <= 5
    for trial in range(args.trials):
        output = run('cmd', 'activity', 'start-activity', '-W', '-S', '-n', args.launch)
        report['trials'].append(output)
        print(output, flush=True)
        time.sleep(2)
path = Path(__file__).parent / 'output' / ('performance-' + Path(args.label).name + '.json')
path.write_text(json.dumps(report, indent=2), encoding='utf-8')
print(path.resolve())
