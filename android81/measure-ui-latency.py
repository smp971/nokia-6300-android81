"""Serial-pinned keypad dispatch/draw timing; output is private, not optical latency."""
import argparse,json,re,shlex,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--label',required=True);args=p.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def shell(c):
 r=subprocess.run(adb+['shell','/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(c)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
assert shell('cat /sys/class/input/event1/device/name').strip()=='matrix_keypad'
try:
 shell('cmd activity start-activity -n ro.leo.shell/.MainActivity --ez trace_input true')
 time.sleep(3)
 focus=shell('dumpsys window windows | grep mCurrentFocus')
 assert 'ro.leo.shell' in focus and 'Not Responding' not in focus,focus
 shell('setprop debug.leo.ui_latency 1; logcat -c')
 commands=[]
 for i in range(16):
  code=108 if i%2==0 else 103
  commands.append(f'sendevent /dev/input/event1 1 {code} 1; sendevent /dev/input/event1 0 0 0; sleep 0.06; sendevent /dev/input/event1 1 {code} 0; sendevent /dev/input/event1 0 0 0; sleep 1.2')
 shell('; '.join(commands));time.sleep(2)
 logs=shell('logcat -d -v brief -s LeoInputTrace:I hwcomposer.leo:I *:S')
 # HWC's default LOG_TAG can vary; capture its token regardless of tag.
 logs=shell('logcat -d -v brief')
finally:
 shell('setprop debug.leo.ui_latency 0; cmd activity start-activity -n ro.leo.shell/.MainActivity --ez trace_input false')
keys={int(a):(int(b),int(c)) for a,b,c in re.findall(r'key seq=(\d+) event_ms=(\d+) dispatch_ns=(\d+)',logs)}
draws={int(a):int(b) for a,b in re.findall(r'draw seq=(\d+) draw_ns=(\d+)',logs)}
presents=[(int(a),int(b)) for a,b in re.findall(r'LeoPresent frame=\d+ begin_ns=(\d+) done_ns=(\d+)',logs)]
rows=[]
for seq,(event,dispatch) in keys.items():
 if seq not in draws:continue
 draw=draws[seq];row={'sequence':seq,'dispatch_ms':dispatch/1e6-event,'event_to_draw_ms':draw/1e6-event}
 following=next(((a,b) for a,b in presents if a>=draw and a-draw<1e9),None)
 if following:row['event_to_next_pan_done_ms']=following[1]/1e6-event
 rows.append(row)
report={'label':args.label,'samples':rows,'note':'Injected Linux key event to onDraw and optional next framebuffer pan completion; no optical timing or guaranteed frame identity.'}
out=Path(__file__).parent/'output';(out/('ui-latency-'+Path(args.label).name+'.json')).write_text(json.dumps(report,indent=2));(out/('ui-latency-'+Path(args.label).name+'.log')).write_text(logs)
assert len(rows)>=12, 'Insufficient input/draw samples: inspect focus and private log.'
print(json.dumps(report,indent=2))
