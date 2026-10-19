"""One audio module trial with live host-side kernel/logcat capture; no flashing.

--load performs a staged core-only load with Android audio services stopped.
--load-machine additionally reaches the previously crashing sound-card stage.
--quiesce-framework stops zygote and compacts memory before loading. It leaves
Qualcomm HAL activation disabled and never rearms automatic audio loading.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import re
import shlex
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--load', action='store_true')
parser.add_argument('--load-machine',action='store_true')
parser.add_argument('--quiesce-framework',action='store_true')
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
assert not args.load_machine or args.load,'--load-machine requires --load'
assert native('/system/bin/uname','-r')=='4.9.240-perf-g4f9e270fd77f'
for path in (root.parent/'analysis/system/lib/modules').glob('audio*.ko'):
    expected=hashlib.sha256(path.read_bytes()).hexdigest()
    assert native('/system/bin/sha256sum','/vendor/leo-audio/modules/'+path.name).split()[0]==expected,'Changed module '+path.name
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
result = {'successful': False, 'qualcomm_hal_activated': False, 'automatic_audio_armed': False,
          'machine_requested':args.load_machine,'framework_quiesced':args.quiesce_framework}
framework_stopped=False
watchdog_marker='/tmp/leo-audio-probe-done-'+folder.name
def mark(name):
    global stage
    assert all(process.poll() is None for process in streams), 'Live capture ended; refusing next stage'
    stage = name
    stage_log.write(json.dumps({'stage': name, 'host_time': time.time()}) + '\n')
    print(name, flush=True)

try:
    trial_uptime=float(native('/system/bin/cat','/proc/uptime').split()[0])
    # The previous late-boot trial logged a failed 256 KiB rtac allocation.
    # A detached watchdog restores only services, never reloads kernel modules.
    if args.quiesce_framework:
        mark('prepare_framework_restore_watchdog')
        native('/system/bin/sh','-c','nohup /system/bin/sh -c '+shlex.quote('sleep 120; if [ ! -f '+watchdog_marker+' ]; then setprop ctl.start audio-hal-2-0; setprop ctl.start audioserver; setprop ctl.start zygote; fi')+' >/tmp/leo-audio-watchdog.log 2>&1 </dev/null &')
        native('/system/bin/setprop','ctl.stop','zygote');framework_stopped=True
        time.sleep(3)
    mark('stop_android_audio_services')
    native('/system/bin/setprop', 'ctl.stop', 'audioserver')
    native('/system/bin/setprop', 'ctl.stop', 'audio-hal-2-0')
    time.sleep(2)
    if args.quiesce_framework:
        mark('compact_memory_before_core_modules')
        (folder/'memory-before-private.txt').write_text(native('/system/bin/sh','-c','cat /proc/meminfo; cat /proc/buddyinfo'))
        native('/system/bin/sh','-c','sync; echo 3 > /proc/sys/vm/drop_caches; echo 1 > /proc/sys/vm/compact_memory')
        (folder/'memory-compacted-private.txt').write_text(native('/system/bin/sh','-c','cat /proc/meminfo; cat /proc/buddyinfo'))
    for name, module in [('audio_q6_notifier', 'q6_notifier_dlkm'), ('audio_apr', 'apr_dlkm'),
                         ('audio_adsp_loader', 'adsp_loader_dlkm'), ('audio_q6', 'q6_dlkm'),
                         ('audio_wcd_core', 'wcd_core_dlkm'), ('audio_wcd9xxx', 'wcd9xxx_dlkm'),
                         ('audio_mbhc', 'mbhc_dlkm'), ('audio_stub', 'stub_dlkm'),
                         ('audio_platform', 'platform_dlkm'), ('audio_digital_cdc', 'digital_cdc_dlkm'),
                         ('audio_analog_cdc', 'analog_cdc_dlkm'), ('audio_machine_int_msm8909', 'machine_int_dlkm')]:
        if name=='audio_machine_int_msm8909' and not args.load_machine:
            mark('core_complete_machine_not_requested');break
        loaded = native('/system/bin/cat', '/proc/modules')
        if not re.search(r'^' + re.escape(module) + ' ', loaded, re.M):
            mark('before_' + name)
            native('/system/bin/insmod', '/vendor/leo-audio/modules/' + name + '.ko')
            mark('after_' + name)
            time.sleep(2)
            if name=='audio_q6':
                recent=native('/system/bin/dmesg')
                (folder/'after-q6-private.txt').write_text(recent)
                # With no earlier q6 load in this boot, an allocation warning is
                # sufficient reason to avoid the more dangerous machine stage.
                fresh=[line for line in recent.splitlines() if (stamp:=re.match(r'\[\s*([0-9.]+)\]',line)) and float(stamp.group(1))>=trial_uptime]
                assert not any('page allocation failure' in line for line in fresh),'Kernel allocation failure: stop before machine module'
        if name == 'audio_adsp_loader':
            mark('boot_adsp')
            native('/system/bin/sh', '-c', 'printf 1 > /sys/kernel/boot_adsp/boot')
            time.sleep(3)
    mark('all_modules_loaded_hal_still_stopped' if args.load_machine else 'core_modules_loaded_machine_skipped')
    time.sleep(10)
    if args.load_machine:
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
    # Do not leave a functioning phone without its UI after an ordinary failure.
    try:
        native('/system/bin/setprop','ctl.start','audio-hal-2-0',timeout=5)
        native('/system/bin/setprop','ctl.start','audioserver',timeout=5)
        if framework_stopped:native('/system/bin/setprop','ctl.start','zygote',timeout=5)
        native('/system/bin/touch',watchdog_marker,timeout=5)
    except Exception as exc:print('Service restoration unavailable: '+str(exc),flush=True)
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
