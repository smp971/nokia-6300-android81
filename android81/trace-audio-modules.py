"""One audio module trial with live host-side kernel/logcat capture; no flashing.

--load performs one staged load with Android audio services stopped. It leaves
Qualcomm HAL activation disabled and never rearms automatic audio loading.
"""
from pathlib import Path
import argparse
import datetime
import json
import re
import shlex
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--load', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent
adb = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']
def native(*command, timeout=25):
    result = subprocess.run(adb + ['shell', shlex.join(['/sbin/leo-framework-exec', *command])],
                            capture_output=True, timeout=timeout)
    if result.returncode:
        detail = (result.stderr.decode(errors='replace') + result.stdout.decode(errors='replace')).strip()
        raise RuntimeError(f'Command exited {result.returncode}: {detail or "transport ended without diagnostic output"}')
    return result.stdout.decode(errors='replace').replace('\r', '').strip()

assert 'androidboot.serialno=695d ' in native('/system/bin/cat', '/proc/cmdline')
assert native('/system/bin/getprop', 'sys.boot_completed') == '1'
assert native('/system/bin/getprop', 'persist.leo.audio.autoload') == '0'
assert native('/system/bin/getprop', 'persist.leo.audio.enabled') == '0'
calls = re.findall(r'mCallState=(\d+)', native('/system/bin/dumpsys', 'telephony.registry'))
assert len(calls) == 2 and set(calls) == {'0'}
if not args.load:
    print('Preflight passed. No modules loaded. Explicit --load is required.')
    raise SystemExit(0)
folder = root / 'output/audio-host-trace' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
folder.mkdir(parents=True)
streams, files = [], []
for name, command in [('kernel', ['/system/bin/dmesg', '-w']),
                      ('logcat', ['/system/bin/logcat', '-b', 'all', '-v', 'threadtime'])]:
    handle = (folder / (name + '-private.log')).open('wb', buffering=0)
    files.append(handle)
    streams.append(subprocess.Popen(adb + ['shell', shlex.join(['/sbin/leo-framework-exec', *command])],
                                    stdout=handle, stderr=subprocess.STDOUT))
time.sleep(1)
stage_log = (folder / 'stages.jsonl').open('w', buffering=1)
stage = 'preflight'
result = {'successful': False, 'qualcomm_hal_activated': False, 'automatic_audio_armed': False}
def mark(name):
    global stage
    assert all(process.poll() is None for process in streams), 'Live capture ended; refusing next stage'
    stage = name
    stage_log.write(json.dumps({'stage': name, 'host_time': time.time()}) + '\n')
    print(name, flush=True)

try:
    mark('stop_android_audio_services')
    native('/system/bin/setprop', 'ctl.stop', 'audioserver')
    native('/system/bin/setprop', 'ctl.stop', 'audio-hal-2-0')
    time.sleep(2)
    for name, module in [('audio_q6_notifier', 'q6_notifier_dlkm'), ('audio_apr', 'apr_dlkm'),
                         ('audio_adsp_loader', 'adsp_loader_dlkm'), ('audio_q6', 'q6_dlkm'),
                         ('audio_wcd_core', 'wcd_core_dlkm'), ('audio_wcd9xxx', 'wcd9xxx_dlkm'),
                         ('audio_mbhc', 'mbhc_dlkm'), ('audio_stub', 'stub_dlkm'),
                         ('audio_platform', 'platform_dlkm'), ('audio_digital_cdc', 'digital_cdc_dlkm'),
                         ('audio_analog_cdc', 'analog_cdc_dlkm'), ('audio_machine_int_msm8909', 'machine_int_dlkm')]:
        loaded = native('/system/bin/cat', '/proc/modules')
        if not re.search(r'^' + re.escape(module) + ' ', loaded, re.M):
            mark('before_' + name)
            native('/system/bin/insmod', '/vendor/leo-audio/modules/' + name + '.ko')
            mark('after_' + name)
            time.sleep(2)
        if name == 'audio_adsp_loader':
            mark('boot_adsp')
            native('/system/bin/sh', '-c', 'printf 1 > /sys/kernel/boot_adsp/boot')
            time.sleep(3)
    mark('all_modules_loaded_hal_still_stopped')
    time.sleep(10)
    card = native('/system/bin/cat', '/proc/asound/card0/id')
    assert card == 'msm8909sndcard', card
    (folder / 'cards-private.log').write_text(native('/system/bin/cat', '/proc/asound/cards'))
    mark('module_trial_passed_restore_generic_services')
    native('/system/bin/setprop', 'ctl.start', 'audio-hal-2-0')
    native('/system/bin/setprop', 'ctl.start', 'audioserver')
    result['successful'] = True
except Exception as exc:
    result['error'] = str(exc)
    print('Trial stopped at ' + stage + ': ' + str(exc), flush=True)
finally:
    result['last_stage'] = stage
    (folder / 'result.json').write_text(json.dumps(result, indent=2))
    time.sleep(1)
    for process in streams:
        if process.poll() is None:
            process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    for handle in files:
        handle.close()
    stage_log.close()
    print('Trace folder: ' + str(folder), flush=True)
raise SystemExit(0 if result['successful'] else 1)
