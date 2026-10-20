"""Read-only Leo CPU/memory/battery/USB sampling. Never changes power settings."""
from pathlib import Path
import argparse,json,shlex,subprocess,time
root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--samples',type=int,default=7);parser.add_argument('--interval',type=float,default=10);args=parser.parse_args()
assert 2<=args.samples<=120 and 1<=args.interval<=60
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*command):
    result=subprocess.run(adb+['shell',shlex.join(['/sbin/leo-framework-exec',*command])],capture_output=True,timeout=8)
    if result.returncode:raise RuntimeError('ADB read failed')
    return result.stdout.decode(errors='replace').strip()
def read(path):
    try:return native('/system/bin/cat',path)
    except (RuntimeError,subprocess.TimeoutExpired):return None
def prop(name):return native('/system/bin/getprop',name)
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
samples=[]
for i in range(args.samples):
    try:
        fields=native('/system/bin/cat','/proc/stat').splitlines()[0].split()[1:]
        row={'time':time.time(),'cpu':list(map(int,fields)),'meminfo':native('/system/bin/cat','/proc/meminfo'),'usb':prop('sys.leo.usb.state'),'usb_service':prop('init.svc.leo_usb_transfer'),'audio':prop('sys.leo.audio.state'),'boot':read('/proc/sys/kernel/random/boot_id')}
        for key,path in {'battery_capacity':'/sys/class/power_supply/battery/capacity','battery_status':'/sys/class/power_supply/battery/status','battery_current':'/sys/class/power_supply/battery/current_now','battery_voltage':'/sys/class/power_supply/battery/voltage_now','usb_power':'/sys/devices/platform/soc/78d9000.usb/power/control','usb_runtime':'/sys/devices/platform/soc/78d9000.usb/power/runtime_status'}.items():row[key]=read(path)
        samples.append(row)
    except (RuntimeError,subprocess.TimeoutExpired):samples.append({'time':time.time(),'disconnected':True})
    if i+1<args.samples:time.sleep(args.interval)
valid=[s for s in samples if 'cpu' in s];summary={'samples':len(samples),'adb_failures':len(samples)-len(valid)}
if len(valid)>=2:
    delta=[b-a for a,b in zip(valid[0]['cpu'],valid[-1]['cpu'])];total=sum(delta[:8]);summary['cpu_idle_percent']=round(100*(delta[3]+delta[4])/max(1,total),2)
    summary['same_boot']=len(set(s['boot'] for s in valid))==1;summary['usb_modes']=sorted(set(s['usb'] for s in valid));summary['audio_states']=sorted(set(s['audio'] for s in valid));summary['battery_statuses']=sorted(set(str(s['battery_status']) for s in valid))
out=root/'output/device-health.json';out.write_text(json.dumps({'summary':summary,'samples':samples},indent=2));print(json.dumps(summary,indent=2));print('USB-powered sampling is not an unplugged autonomy measurement.')
