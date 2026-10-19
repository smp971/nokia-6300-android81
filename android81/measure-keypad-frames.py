"""Run a bounded keypad-only Leo benchmark; raw diagnostics remain local/ignored.

Leave the phone untouched. This script opens only Home, Settings or Android Apps.
HWUI frame duration is not the same as key-to-LCD latency.
"""
import argparse,json,re,shlex,statistics,subprocess,time
from pathlib import Path
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--label',required=True)
parser.add_argument('--component',choices=['MainActivity','SettingsActivity','AndroidAppsActivity'],default='SettingsActivity')
parser.add_argument('--trials',type=int,choices=range(1,6),default=3)
args=parser.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_-]{1,64}',args.label),'Use a simple diagnostic label'
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*command):
    return subprocess.check_output(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*command])],timeout=15).decode('utf-8',errors='replace').replace('\r','').strip()
def java(*command):
    return subprocess.check_output(['python',str(root/'framework-command.py'),*command],timeout=35).decode('utf-8',errors='replace').replace('\r','').strip()
def current():return next((s for s in native('/system/bin/dumpsys','activity','activities').splitlines() if 'mResumedActivity:' in s),'')
def percentile(values,q):return sorted(values)[int((len(values)-1)*q)] if values else None
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
calls=re.findall(r'mCallState=(\d+)',native('/system/bin/dumpsys','telephony.registry'))
assert calls and set(calls)=={'0'},'Leave calls idle for the UI benchmark'
assert (root/'output/keypad-frame-bench').is_file(),'Run build-keypad-frame-bench.sh first'
subprocess.run(adb+['push',str(root/'output/keypad-frame-bench'),'/tmp/keypad-frame-bench'],check=True,capture_output=True)
native('/system/bin/chmod','755','/tmp/keypad-frame-bench')
java('/system/bin/sh','/system/bin/am','start','-f','0x14000000','-n','ro.leo.visual/.'+args.component);time.sleep(1)
assert args.component in current(),'The requested Leo page must be visible and unlocked'
if args.component=='MainActivity':java('/system/bin/sh','/system/bin/input','keyevent','22');time.sleep(.5)
out=root/'output/performance'/args.label;out.mkdir(parents=True,exist_ok=True)
trace=native('/system/bin/getprop','debug.leo.ui_latency') or '0'
native('/system/bin/setprop','debug.leo.ui_latency','1');native('/system/bin/log','-t','LeoPerf','BEGIN_'+args.label)
report=[]
try:
    for trial in range(args.trials):
        assert args.component in current(),'UI changed; stop this probe'
        native('/system/bin/dumpsys','gfxinfo','ro.leo.visual','reset')
        native('/tmp/keypad-frame-bench');time.sleep(.4)
        stats=native('/system/bin/dumpsys','gfxinfo','ro.leo.visual','framestats')
        (out/('gfx-'+str(trial)+'.txt')).write_text(stats,encoding='utf-8')
        since=int(re.search(r'Stats since: (\d+)ns',stats).group(1));frames=[]
        for line in stats.splitlines():
            if re.match(r'^0,\d',line):
                values=[int(v) for v in line.rstrip(',').split(',')]
                if len(values)>=14 and values[1]>=since and values[13]>values[1]:frames.append((values[13]-values[1])/1e6)
        item=dict(trial=trial,keys=40,frames=len(frames),median_ms=statistics.median(frames) if frames else None,p95_ms=percentile(frames,.95),hwui_percentiles={})
        for q in [50,90,95,99]:
            match=re.search(str(q)+r'th percentile: (\d+)ms',stats);item['hwui_percentiles'][str(q)]=int(match.group(1)) if match else None
        report.append(item);print(json.dumps(item),flush=True)
    logs=native('/system/bin/logcat','-d','-t','2000');marker=logs.rfind('BEGIN_'+args.label)
    assert marker>=0,'Log marker lost; run a shorter probe'
    logs=logs[marker:];(out/'log-private.txt').write_text(logs,encoding='utf-8')
    result=dict(label=args.label,component=args.component,trials=report,egl_wait_errors=logs.count('error waiting for EGL fence'),bind_failures=logs.count('bindTextureImage failed'),present_count=logs.count('LeoPresent frame='))
    (out/'summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
finally:native('/system/bin/setprop','debug.leo.ui_latency',trace)
print('Local report: '+str(out/'summary.json'))
