"""Verify boot-started radio services and registration, without calls or data use."""
from pathlib import Path
import json
import re
import shlex
import subprocess
import time

ROOT = Path(__file__).resolve().parent
ADB = ['D:/ROMS/platform-tools/adb.exe', '-s', '695d']


def shell(*args):
    result = subprocess.run(ADB+['shell', shlex.join([
        '/sbin/leo-framework-exec', *args])], capture_output=True, timeout=8)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout.decode(errors='replace').replace('\r', '').strip()


deadline = time.monotonic()+180
previous = ''
while time.monotonic() < deadline:
    try:
        properties = dict(re.findall(r'^\[([^]]+)\]: \[(.*)\]$',
                                    shell('/system/bin/getprop'), re.M))
        names = ('sys.boot_completed', 'init.svc.leo_radio_setup', 'init.svc.leo_rmt_storage',
                 'init.svc.leo_per_mgr', 'init.svc.leo_rild1', 'init.svc.leo_rild2',
                 'gsm.sim.state', 'gsm.operator.alpha', 'gsm.network.type')
        report = {name: properties.get(name, '') for name in names}
        summary = json.dumps(report, ensure_ascii=False)
        if summary != previous:
            print(summary, flush=True)
            previous = summary
        services = all(report['init.svc.'+name] == 'running'
                       for name in ('leo_rmt_storage', 'leo_per_mgr', 'leo_rild1', 'leo_rild2'))
        if report['sys.boot_completed'] == '1' and services and report['gsm.sim.state'] == 'READY,READY':
            registry = shell('/system/bin/dumpsys', 'telephony.registry')
            report['voice_slots_in_service'] = len(re.findall(
                r'^\s*mServiceState=\{mVoiceRegState=0\(IN_SERVICE\)', registry, re.M))
            if report['voice_slots_in_service'] >= 2:
                report['setup_log'] = shell('/system/bin/cat', '/data/local/tmp/leo-radio-setup.log')
                report['uptime'] = shell('/system/bin/cat', '/proc/uptime').split()[0]
                report['boot_id'] = shell('/system/bin/cat', '/proc/sys/kernel/random/boot_id')
                report['system_mount'] = next(line for line in shell('/system/bin/cat', '/proc/1/mounts').splitlines()
                                              if ' /system ' in line)
                report['calls_sms_mobile_data_tested'] = False
                (ROOT/'output/radio-runtime-verified.json').write_text(json.dumps(report, indent=2))
                print('PASS: automatic services, two READY SIMs and both voice slots IN_SERVICE.', flush=True)
                raise SystemExit(0)
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        if previous != 'ADB unavailable':
            print('ADB unavailable: '+str(error).strip(), flush=True)
            previous = 'ADB unavailable'
    time.sleep(4)
raise SystemExit('Radio boot verification timed out; inspect setup log and service states.')
