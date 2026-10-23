"""Verify Leo camera integration. --capture opts into preview and one real JPEG.
Default mode only reads service state; no camera is opened. Photos stay local.
"""
import argparse,subprocess,time,json,re
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--capture",action="store_true");args=parser.parse_args()
from pathlib import Path
fw=['python','-X','utf8',str(Path(__file__).resolve().parent/'framework-command.py')]
def run(*a,timeout=35):return subprocess.check_output(fw+list(a),timeout=timeout).decode(errors='replace').strip()
assert 'androidboot.serialno=695d' in run('/system/bin/cat','/proc/cmdline')
assert 'Number of camera devices: 1' in run('/system/bin/dumpsys','media.camera')
assert run('/system/bin/getprop','init.svc.leo-camera-daemon')=='running'
assert run('/system/bin/getprop','init.svc.leo-camera-provider')=='running'
if not args.capture:
 print('PASS: one public camera; automatic daemon/provider running; no capture requested')
 raise SystemExit(0)
call_states=re.findall(r'mCallState=(\d+)',run('/system/bin/dumpsys','telephony.registry'))
assert call_states and set(call_states)=={'0'},'Finish calls before the UI probe'
def photos():return set(run('/system/bin/sh','-c','ls /sdcard/Pictures/Leo/*.jpg 2>/dev/null || true').splitlines())
def capture_probe():
 run('/system/bin/sh','/system/bin/am','start','-f','0x14000000','-n','ro.leo.visual/.MainActivity')
 main=run('/system/bin/pidof','ro.leo.visual');provider=run('/system/bin/pidof','camera-provider');server=run('/system/bin/pidof','cameraserver')
 before=photos()
 run('/system/bin/sh','/system/bin/input','keyevent','224')
 run('/system/bin/sh','/system/bin/am','start','-f','0x14000000','-n','ro.leo.visual/.CameraActivity')
 for i in range(20):
  focus=run('/system/bin/dumpsys','window','windows')
  if any('mCurrentFocus=' in line and 'CameraActivity' in line for line in focus.splitlines()):break
  time.sleep(.2)
 else:raise RuntimeError('Camera window did not get focus')
 time.sleep(.5)
 run('/system/bin/sh','/system/bin/input','keyevent','20');run('/system/bin/sh','/system/bin/input','keyevent','23')
 ready=False
 for i in range(5):
  run('/system/bin/sh','/system/bin/uiautomator','dump','--compressed','/tmp/leo-camera-ui.xml',timeout=45)
  xml=run('/system/bin/cat','/tmp/leo-camera-ui.xml')
  if 'OK declanșează' in xml:
   assert xml.count('text="Foto"')==1,'Duplicated preview footer'
   ready=True;break
  if 'Cameră oprită' in xml:raise RuntimeError('Preview reported failure')
  time.sleep(.5)
 assert ready,'Preview not ready'
 camera=run('/system/bin/pidof','ro.leo.visual:camera');assert camera!=main
 print('Separate camera process ready; main PID '+main,flush=True)
 run('/system/bin/sh','/system/bin/input','keyevent','23')
 new=set()
 for i in range(20):
  new=photos()-before
  if new:break
  time.sleep(.5)
 assert len(new)==1,new
 print('JPEG saved',flush=True)
 run('/system/bin/sh','/system/bin/am','start','-f','0x14000000','-n','ro.leo.visual/.MainActivity')
 for i in range(20):
  state=run('/system/bin/dumpsys','media.camera')
  if 'Client Package Name: ro.leo.visual' not in state:break
  time.sleep(.5)
 else:raise RuntimeError('Camera not released')
 assert run('/system/bin/pidof','ro.leo.visual')==main,'Launcher restarted'
 assert run('/system/bin/pidof','camera-provider')==provider,'Provider restarted'
 assert run('/system/bin/pidof','cameraserver')==server,'Camera service restarted'
 assert run('/system/bin/getprop','sys.leo.audio.state')=='active'
 report=dict(result='PASS',main_pid_unchanged=True,provider_pid_unchanged=True,server_pid_unchanged=True,separate_camera_process=True,jpeg_saved=True,camera_released=True,audio_state='active')
 (Path(__file__).resolve().parent/'output/camera-isolated-ui-validation.json').write_text(json.dumps(report,indent=2))
 print('PASS preview, JPEG, release; launcher/provider/server retained PIDs',flush=True)
try:
 capture_probe()
finally:
 run('/system/bin/sh','/system/bin/am','start','-f','0x14000000','-n','ro.leo.visual/.MainActivity')
