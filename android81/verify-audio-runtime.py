"""Verify active Qualcomm audio; --accept arms subsequent ordinary boots."""
from pathlib import Path
import argparse,json,shlex,subprocess,time
p=argparse.ArgumentParser();p.add_argument('--wait',action='store_true');p.add_argument('--accept',action='store_true');args=p.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d','shell']
def shell(s):
 r=subprocess.run(adb+['/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(s)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=12)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip()
deadline=time.monotonic()+(210 if args.wait else 1);previous=None
while True:
 try:
  assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
  state=shell('getprop sys.leo.audio.state')
  if state!=previous:print('Audio state:',state or '(booting)',flush=True);previous=state
  if state=='active':break
  if state=='failed':raise SystemExit('Audio setup failed; generic fallback requested.')
 except RuntimeError:
  if not args.wait:raise
 if time.monotonic()>deadline:raise SystemExit('Audio did not become active within the wait window.')
 time.sleep(3)
assert shell('cat /proc/asound/card0/id')=='msm8909sndcard'
for service in ['audioserver','audio-hal-2-0']:assert shell('getprop init.svc.'+service)=='running'
pid=shell('pidof android.hardware.audio@2.0-service');maps=shell('cat /proc/'+pid+'/maps')
assert '/vendor/leo-audio/lib/audio.primary.msm8909.so' in maps
assert '/vendor/leo-audio/lib/audio-primary-generic.so' not in maps
assert ' /system ext4 ro,' in shell('cat /proc/mounts')
policy=shell('dumpsys media.audio_policy');assert 'Primary Output: 0' not in policy and 'Primary Output:' in policy
if args.accept:
 shell('mkdir -p /data/leo-audio-trace; chmod 700 /data/leo-audio-trace; touch /data/leo-audio-trace/early-arm; chmod 600 /data/leo-audio-trace/early-arm; sync; setprop persist.leo.audio.validated 1; setprop persist.leo.audio.autoload 1')
report={'boot_id':shell('cat /proc/sys/kernel/random/boot_id'),'card':'msm8909sndcard','native_audio_running':True,'stock_hal_loaded':True,'generic_fallback_loaded':False,'system_read_only':True,'accepted':args.accept,'checks_are_software_only':True,'physical_confirmation':'Documented separately in AUDIO-BRINGUP.md; not measured by this script','microphone_and_call_audio':'not tested'}
(Path(__file__).resolve().parent/'output/audio-runtime-verified.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
