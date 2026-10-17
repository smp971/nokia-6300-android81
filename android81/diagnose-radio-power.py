"""Capture radio-power queues and worker stacks without issuing radio requests."""
from pathlib import Path
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parent
OUT = ROOT/'output/radio-power'/time.strftime('%Y%m%d-%H%M%S')
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']


def shell(*args, timeout=25):
    result = subprocess.run(ADB+['shell', shlex.join([
        '/sbin/leo-framework-exec', *args])], capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace') or
                           result.stdout.decode(errors='replace'))
    return result.stdout


if __name__ == '__main__':
    # Check connectivity before creating a report; never switch to another phone.
    processes = shell('/system/bin/ps', '-A', '-o', 'PID,ARGS').decode()
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT/'processes.txt').write_text(processes)
    for name, args in {
        'radio-log': ['/system/bin/logcat', '-b', 'radio', '-d', '-t', '8000'],
        'kernel': ['/system/bin/dmesg'],
        'properties': ['/system/bin/getprop'],
        'registration': ['/system/bin/dumpsys', 'telephony.registry'],
        'crashes': ['/system/bin/logcat', '-b', 'crash', '-d', '-t', '200'],
    }.items():
        (OUT/(name+'.txt')).write_bytes(shell(*args))
    # Only inspect the staged vendor trial; exclude the installed idle reference RIL.
    for line in processes.splitlines():
        fields = line.split(None, 1)
        if len(fields) != 2 or not fields[0].isdigit():
            continue
        pid, command = fields
        vendor = any(path in command for path in ('/tmp/leo-radio-probe/lib/', '/system/vendor/leo-radio/lib/'))
        if not command.startswith('rild ') or not vendor:
            continue
        script = (f'for d in /proc/{pid}/task/*; do echo "$d"; '
                  'cat "$d/comm" "$d/wchan"; echo; done')
        (OUT/f'threads-{pid}.txt').write_bytes(shell('/system/bin/sh', '-c', script))
        try:
            stack = shell('/system/bin/debuggerd', '-b', pid, timeout=35)
            (OUT/f'native-stacks-{pid}.txt').write_bytes(stack)
        except (RuntimeError, subprocess.TimeoutExpired) as error:
            (OUT/f'native-stacks-{pid}-error.txt').write_text(str(error))
    print(f'Private diagnostics saved to {OUT}')
