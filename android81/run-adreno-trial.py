"""Run one staged, unguarded GPU trial with bounded live kernel-log capture.

May expose a kernel GPU bug. Never flashes or selects Adreno for Android UI.
"""
from pathlib import Path
import hashlib
import os
import shlex
import subprocess
import threading
import time

here = Path(__file__).resolve().parent
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
def shell(command, timeout=10):
    return subprocess.run(adb + ['shell', '/sbin/leo-framework-exec /system/bin/sh -c ' +
        shlex.quote(command)], capture_output=True, timeout=timeout)
assert b'androidboot.serialno=695d' in shell('cat /proc/cmdline').stdout
for name in ('a300_pfp.fw', 'a300_pm4.fw'):
    expected = hashlib.sha256((here.parent / 'analysis/system/etc/firmware' / name).read_bytes()).hexdigest()
    assert expected.encode() in shell('sha256sum /tmp/adreno-fw/' + name).stdout
capture = here / 'output/adreno-corrected-live-kernel.log'
watcher = subprocess.Popen(adb + ['shell', '/sbin/leo-framework-exec /system/bin/dmesg -w'],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
ready = threading.Event()
def collect():
    with capture.open('wb', buffering=0) as output:
        last_sync = time.monotonic()
        for line in iter(watcher.stdout.readline, b''):
            output.write(line)
            if time.monotonic() - last_sync > 0.2:
                os.fsync(output.fileno())
                last_sync = time.monotonic()
            ready.set()
thread = threading.Thread(target=collect, daemon=True)
thread.start()
try:
    assert ready.wait(5) and watcher.poll() is None, 'Live kernel capture did not start'
    result = shell('chmod 755 /tmp/adreno-probe; '
        'sh /tmp/adreno-trial.sh > /data/local/tmp/leo-adreno-corrected.log 2>&1 < /dev/null &')
    assert result.returncode == 0, result.stderr
    print('Live capture active; corrected trial launched.', flush=True)
    for _ in range(15):
        time.sleep(2)
        try:
            result = shell('cat /data/local/tmp/leo-adreno-corrected.log')
        except subprocess.TimeoutExpired:
            print('ADB read timed out; do not repeat the trial.', flush=True)
            break
        if result.returncode:
            print('ADB interrupted; inspect USB and phone state.', flush=True)
            break
        if b'trial_exit=' in result.stdout:
            break
    else:
        print('No final status within observation window.', flush=True)
    if result.returncode == 0:
        path = here / 'output/adreno-corrected-trial.log'
        path.write_bytes(result.stdout)
        print(result.stdout.decode('utf-8', errors='replace'), flush=True)
finally:
    watcher.terminate()
    try:
        watcher.wait(timeout=3)
    except subprocess.TimeoutExpired:
        watcher.kill(); watcher.wait()
    thread.join(timeout=2)
    print('Kernel capture:', capture.resolve(), flush=True)
