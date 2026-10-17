"""Temporary stock audio-module bring-up on the serial-pinned, matching kernel."""
from pathlib import Path
import hashlib,re,shlex,subprocess,json,time,argparse
parser=argparse.ArgumentParser();parser.add_argument("--load-core",action="store_true",help="Load the first 11 modules in RAM, with per-stage diagnostics.");parser.add_argument("--load-machine",action="store_true",help="Experimental: previous attempt crashed at this stage; requires live kernel capture.");args=parser.parse_args()
here=Path(__file__).resolve().parent;root=here.parent;adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def call(*a):
 r=subprocess.run(adb+list(a),capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=30)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout.strip()
def shell(s):return call('shell','/sbin/leo-framework-exec /system/bin/sh -c '+shlex.quote(s))
assert 'androidboot.serialno=695d' in shell('cat /proc/cmdline')
release=shell('uname -r');assert release=='4.9.240-perf-g4f9e270fd77f'
order=['audio_q6_notifier','audio_apr','audio_adsp_loader','audio_q6','audio_wcd_core','audio_wcd9xxx','audio_mbhc','audio_stub','audio_platform','audio_digital_cdc','audio_analog_cdc','audio_machine_int_msm8909']
if not args.load_core:
 print('Matching kernel and stock modules identified. No modules loaded; use --load-core only for the documented diagnostic.')
 raise SystemExit(0)
shell('mkdir -p /tmp/leo-audio-modules')
for name in order:
 p=root/'analysis/system/lib/modules'/(name+'.ko');b=p.read_bytes()
 assert re.search(rb'vermagic=([^\x00]+)',b).group(1).decode().startswith(release+' ')
 call('push',str(p),'/tmp/leo-audio-modules/'+p.name)
 assert shell('sha256sum /tmp/leo-audio-modules/'+p.name).split()[0]==hashlib.sha256(b).hexdigest()
for name in order:
 if name=='audio_machine_int_msm8909' and not args.load_machine:
  print('Machine module skipped after the previous crash.',flush=True);break
 print('Loading '+name,flush=True)
 print(shell('insmod /tmp/leo-audio-modules/'+name+'.ko'),flush=True)
 if name=='audio_adsp_loader':print(shell('printf 1 > /sys/kernel/boot_adsp/boot'),flush=True)
 time.sleep(.5)
 (here/'output'/('audio-after-'+name+'.txt')).write_text(shell('cat /proc/asound/cards; cat /proc/modules; dmesg | tail -100'),encoding='utf-8')
print(shell('cat /proc/asound/cards; cat /proc/asound/pcm; ls -l /dev/snd'))
(here/'output/audio-modules-live.txt').write_text(shell('cat /proc/modules; dmesg | tail -130'),encoding='utf-8')
