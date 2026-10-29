"""Bounded SIM 2 WFC-enable trial, with independent on-device restoration.

Requires the verified Orange/DDS slot, idle calls and validated Wi-Fi. Does not
change LTE/DDS, Wi-Fi preference, APNs, IMS provisioning or place calls.
"""
from pathlib import Path
import argparse, json, re, subprocess, time, uuid

ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def fw(*args,timeout=25):
    return subprocess.check_output(['python','-X','utf8',str(ROOT/'framework-command.py'),
        '--timeout',str(timeout),*args],timeout=timeout+5).decode('utf-8',errors='replace').strip()
def shell(command):return fw('/system/bin/sh','-c',command)
def idle():return re.findall(r'^\s*mCallState=(\d+)',fw('/system/bin/dumpsys','telephony.registry'),re.M)==['0','0']
def run():
    devices=subprocess.check_output(ADB[:1]+['devices'],text=True)
    assert re.search(r'^695d\s+device$',devices,re.M),'Nokia missing'
    assert idle(),'Calls must be idle'
    assert fw('/system/bin/getprop','sys.leo.ims.slot')=='1','Selected IMS must be SIM 2'
    assert fw('/system/bin/settings','get','global','multi_sim_data_call')=='2','DDS changed'
    operators=fw('/system/bin/getprop','gsm.sim.operator.numeric').split(',')
    assert len(operators)>1 and operators[1]=='22610','Verified Orange SIM 2 required'
    network=fw('/system/bin/dumpsys','connectivity')
    assert any('type: WIFI' in line and 'CONNECTED/CONNECTED' in line and 'VALIDATED' in line for line in network.splitlines()),'Validated Wi-Fi required'
    assert fw('/system/bin/getprop','sys.leo.ims.voice')=='1','VoLTE baseline missing'
    remote='/tmp/leo-wfc-'+uuid.uuid4().hex[:8]
    local=ROOT/'output'/remote.rsplit('/',1)[1];local.mkdir()
    fw('/system/bin/mkdir',remote)
    binary=remote+'/control'
    subprocess.run(ADB+['push',str(ROOT/'output/wfc-control'),binary],check=True,capture_output=True)
    fw('/system/bin/chmod','755',binary)
    env='LD_LIBRARY_PATH=/system/vendor/leo-radio/lib:/system/vendor/lib:/system/lib LD_PRELOAD=/system/vendor/leo-radio/shim.so '
    before=shell(env+binary)
    assert 'binding=1 wfc=0 voice=1 registration_enabled=1' in before,before
    (local/'baseline.txt').write_text(before,encoding='utf-8')
    restore=local/'restore.sh'
    restore.write_text('#!/system/bin/sh\nexport PATH=/system/bin\nsleep 75\n'
        'test -f '+remote+'/restored && exit 0\n'
        'while :; do\n'
        '  if [ "$(dumpsys telephony.registry | grep -c "mCallState=0")" = 2 ]; then\n'
        '    '+env+binary+' --restore-disabled && { touch '+remote+'/restored; exit 0; }\n'
        '  fi\n  sleep 5\ndone\n',encoding='utf-8',newline='\n')
    subprocess.run(ADB+['push',str(restore),remote+'/restore.sh'],check=True,capture_output=True)
    # Start inside Android's mount namespace/environment; survives ADB loss.
    shell('nohup /system/bin/sh '+remote+'/restore.sh </dev/null >'+remote+'/restore.log 2>&1 &')
    assert idle(),'Call arrived before enable'
    reports=[]
    try:
        enabled=shell(env+binary+' --enable-trial');reports.append(enabled);print(enabled,flush=True)
        for index in range(3):
            time.sleep(12)
            report=shell(env+binary);reports.append(report);print('sample='+str(index+1)+'\n'+report,flush=True)
    finally:
        if idle():
            restored=shell(env+binary+' --restore-disabled');reports.append(restored)
            assert 'verified_wfc=0' in restored,restored
            fw('/system/bin/touch',remote+'/restored')
            print('WFC flag restored to disabled; no calls placed.',flush=True)
        else:print('Call active: independent restore waits for idle.',flush=True)
        (local/'trial.json').write_text(json.dumps({'reports':reports,'remote':remote},indent=2),encoding='utf-8')
    print('Reports: '+str(local),flush=True)
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',action='store_true');args=parser.parse_args()
    if args.run:run()
    else:parser.print_help()
