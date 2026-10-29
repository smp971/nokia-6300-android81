"""Guarded IMS trial/deployment with an independent on-phone rollback.

--run temporarily replaces only the Java IMS client for read-only observation.
--install-refresh deploys the normal selected-slot client and retains it only
after live voice-capability verification. Its ordinary IMS configuration runs.
Native daemons, modem selection, APNs, system partitions and call/SMS records
are not modified. Neither operation places a call or sends an SMS.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shlex
import subprocess
import tarfile
import time
import uuid

ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']


def fw(*args,timeout=25):
    return subprocess.check_output(['python','-X','utf8',str(ROOT/'framework-command.py'),
        '--timeout',str(timeout),*args],timeout=timeout+5).decode('utf-8',errors='replace').strip()


def shell(text,timeout=25):return fw('/system/bin/sh','-c',text,timeout=timeout)
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def push(local,remote):subprocess.run(ADB+['push',str(local),remote],check=True,capture_output=True,timeout=30)


def idle():
    return re.findall(r'^\s*mCallState=(\d+)',fw('/system/bin/dumpsys','telephony.registry'),re.M)==['0','0']


def stop_client():
    try:
        shell('/system/bin/am stopservice -n org.codeaurora.ims/.ImsService 2>&1')
    except subprocess.CalledProcessError as error:
        # Oreo's stopservice command may return -1 even after reporting success.
        # Accept only that explicit response; verify process absence afterwards.
        output=error.output.decode('utf-8',errors='replace') if isinstance(error.output,bytes) else str(error.output)
        if 'Service stopped' not in output and 'Service not stopped: was not running.' not in output:raise


def kill_own(name):
    pids=shell('/system/bin/pidof '+shlex.quote(name)+' || true').split()
    for pid in pids:
        assert pid.isdigit()
        actual=fw('/system/bin/cat','/proc/'+pid+'/cmdline').split('\0')[0]
        assert actual==name,'Unexpected process identity'
        assert idle(),'Call began; leave client intact'
        fw('/system/bin/kill','-9',pid)


def run(install_refresh=False):
    assert 'androidboot.serialno=695d' in fw('/system/bin/cat','/proc/cmdline')
    assert idle(),'Finish calls before the local observation trial'
    assert fw('/system/bin/getprop','sys.leo.ims.native_ready')=='1','Existing native IMS backend must be ready'
    assert fw('/system/bin/getprop','sys.leo.ims.native')=='1'
    candidate=ROOT/'output/ims-reference'/('ported-ims.apk' if install_refresh else 'ported-ims-dual.apk')
    assert candidate.is_file()
    stamp=time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6]
    local=ROOT/'output/ims-dual'/stamp;local.mkdir(parents=True)
    remote='/tmp/leo-ims-dual-'+stamp
    shell('/system/bin/mkdir -m 700 '+remote)
    package=fw('/system/bin/sh','/system/bin/pm','path','org.codeaurora.ims')
    assert package.startswith('package:/') and '\n' not in package
    fw('/system/bin/cp',package[8:],remote+'/before.apk')
    subprocess.run(ADB+['pull',remote+'/before.apk',str(local/'before.apk')],check=True,capture_output=True)
    assert fw('/system/bin/sha256sum',remote+'/before.apk').split()[0]==digest(local/'before.apk')
    properties={key:fw('/system/bin/getprop',key) for key in ['sys.leo.ims.dual','sys.leo.ims.readonly','sys.leo.ims.slot']}
    assert properties['sys.leo.ims.dual'] in ('','0') and properties['sys.leo.ims.readonly'] in ('','0'),'Another IMS trial is active'
    watchdog=fw('/system/bin/getprop','init.svc.leo_ims_watchdog')
    enabled=re.search(r'User 0:.*?enabled=(\d+)',fw('/system/bin/dumpsys','package','org.codeaurora.ims'))
    assert enabled and enabled[1] in ('0','1'),'IMS package must initially be enabled'
    enabled=int(enabled[1])
    modes={slot:fw('/system/bin/stat','-c','%a','/dev/socket/qmux_radio/rild_ims'+str(slot)) for slot in (0,1)}
    assert all(re.fullmatch('[0-7]{3,4}',v) for v in modes.values())
    snapshot={'before_sha256':digest(local/'before.apk'),'candidate_sha256':digest(candidate),'properties':properties,'socket_modes':modes,'watchdog':watchdog,'enabled_state':enabled}
    (local/'before.json').write_text(json.dumps(snapshot,indent=2),encoding='utf-8')
    with tarfile.open(ROOT/'output/framework-root.tar') as archive:
        env=archive.extractfile('./init.environ.rc').read().decode()
    exports=[]
    for line in env.splitlines():
        fields=line.split()
        if len(fields)==3 and fields[0]=='export':exports.append('export '+fields[1]+'='+shlex.quote(fields[2]))
    lines=['#!/system/bin/sh','export PATH=/system/bin',*exports,
        'test -f '+remote+'/done && exit 0',
        'mkdir '+remote+'/restore-lock 2>/dev/null || exit 0',
        "trap 'rmdir "+remote+"/restore-lock' EXIT",
        # Incoming CS calls can still occur: no forced teardown while any call exists.
        'while [ "$(dumpsys telephony.registry | grep -c "mCallState=0")" != 2 ]; do sleep 5; done',
        'pm disable --user 0 org.codeaurora.ims',
        'am stopservice -n org.codeaurora.ims/.ImsService',
        'sleep 1',
        *['for taskpid in $(pidof '+name+'); do [ "$(tr -d "\\000" </proc/$taskpid/cmdline)" = '+name+' ] && kill -9 "$taskpid"; done' for name in ['org.codeaurora.ims:leo_ims','org.codeaurora.ims:leo_controller']],
        'if ! sh /system/bin/pm install -r -d '+remote+'/before.apk; then echo APK_RESTORE_FAILED; exit 1; fi',
        *['setprop '+key+' '+shlex.quote(value) for key,value in properties.items()],
        *['chmod '+mode+' /dev/socket/qmux_radio/rild_ims'+str(slot) for slot,mode in modes.items()],
        'pm '+('default-state' if enabled==0 else 'enable')+' --user 0 org.codeaurora.ims',
        'cmd deviceidle tempwhitelist -d 20000 org.codeaurora.ims',
        'am startservice -n org.codeaurora.ims/.LeoImsController',
        *(['setprop ctl.start leo_ims_watchdog'] if watchdog=='running' else []),
        'touch '+remote+'/done','echo RESTORED','']
    restore=local/'restore.sh';restore.write_text('\n'.join(lines),encoding='utf-8',newline='\n')
    push(restore,remote+'/restore.sh')
    timer=local/'timer.sh';timer.write_text('#!/system/bin/sh\nsleep 90\nexec /system/bin/sh '+remote+'/restore.sh\n',encoding='utf-8',newline='\n')
    push(timer,remote+'/timer.sh')
    push(candidate,remote+'/candidate.apk')
    assert fw('/system/bin/sha256sum',remote+'/candidate.apk').split()[0]==digest(candidate)
    shell('/system/bin/nohup /system/bin/sh '+remote+'/timer.sh </dev/null >'+remote+'/timer.log 2>&1 &')
    accepted=False
    try:
        fw('/system/bin/setprop','ctl.stop','leo_ims_watchdog')
        fw('/system/bin/sh','/system/bin/pm','disable','--user','0','org.codeaurora.ims')
        kill_own('org.codeaurora.ims:leo_controller')
        assert idle()
        stop_client()
        kill_own('org.codeaurora.ims:leo_ims')
        time.sleep(1)
        assert not shell('/system/bin/pidof org.codeaurora.ims:leo_ims || true'),'Old transport still active'
        if not install_refresh:
            for key in ('sys.leo.ims.dual','sys.leo.ims.readonly'):fw('/system/bin/setprop',key,'1')
            for slot in (0,1):fw('/system/bin/chmod','660','/dev/socket/qmux_radio/rild_ims'+str(slot))
        result=fw('/system/bin/sh','/system/bin/pm','install','-r',remote+'/candidate.apk',timeout=45)
        assert 'Success' in result,result
        fw('/system/bin/sh','/system/bin/pm','enable','--user','0','org.codeaurora.ims')
        fw('/system/bin/cmd','deviceidle','tempwhitelist','-d','20000','org.codeaurora.ims')
        fw('/system/bin/sh','/system/bin/am','startservice','-n','org.codeaurora.ims/.'+('LeoImsController' if install_refresh else 'ImsService'))
        time.sleep(8)
        report=fw('/system/bin/dumpsys','activity','service','org.codeaurora.ims/.ImsService')
        (local/'client-report.txt').write_text(report,encoding='utf-8')
        observed=re.findall(r'LEO_IMS slot=(\d) phone=(\d) registration=(\d) voice_available=(true|false)',report)
        if install_refresh:
            assert len(observed)==1 and observed[0]==(properties['sys.leo.ims.slot'],properties['sys.leo.ims.slot'],'1','true'),report
            fw('/system/bin/sh','/system/bin/pm','default-state' if enabled==0 else 'enable','--user','0','org.codeaurora.ims')
            if watchdog=='running':fw('/system/bin/setprop','ctl.start','leo_ims_watchdog')
            fw('/system/bin/sh','/system/bin/am','startservice','-n','org.codeaurora.ims/.LeoImsController')
            time.sleep(5)
            assert fw('/system/bin/getprop','sys.leo.ims.voice')=='1','Voice capability did not survive controller restart'
            current=fw('/system/bin/sh','/system/bin/pm','path','org.codeaurora.ims')[8:]
            assert fw('/system/bin/sha256sum',current).split()[0]==digest(candidate)
            fw('/system/bin/touch',remote+'/done')
            accepted=True
            (ROOT/'output/ims-refresh-installed.json').write_text(json.dumps({'sha256':digest(candidate),'backup':str(local),'selected_slot':properties['sys.leo.ims.slot']},indent=2),encoding='utf-8')
        else:
            assert len(observed)==2 and {(r[0],r[1]) for r in observed}=={('0','0'),('1','1')},report
        print(report,flush=True)
    finally:
        # The device timer covers host failure or ADB disconnect as well.
        if not accepted:
            restore_output=fw('/system/bin/sh',remote+'/restore.sh',timeout=60)
            (local/'restore-report.txt').write_text(restore_output,encoding='utf-8')
        assert shell('test -f '+remote+'/done && echo yes')=='yes','Rollback not finished; inspect on-device timer'
        installed=fw('/system/bin/sh','/system/bin/pm','path','org.codeaurora.ims')[8:]
        assert fw('/system/bin/sha256sum',installed).split()[0]==(digest(candidate) if accepted else snapshot['before_sha256']),'Package digest mismatch'
        assert all(fw('/system/bin/getprop',k)==v for k,v in properties.items()),'Trial property not restored'
        assert all(fw('/system/bin/stat','-c','%a','/dev/socket/qmux_radio/rild_ims'+str(slot))==mode for slot,mode in modes.items()),'Socket mode not restored'
        actual_enabled=re.search(r'User 0:.*?enabled=(\d+)',fw('/system/bin/dumpsys','package','org.codeaurora.ims'))
        assert actual_enabled and int(actual_enabled[1])==enabled,'Package enabled state not restored'
        print(('Capability-refresh fix retained; ' if accepted else 'Original APK restored; ')+'properties/socket modes/package state verified. Reports: '+str(local),flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',action='store_true',help='Execute the bounded on-phone observation and restore the original client')
    parser.add_argument('--install-refresh',action='store_true',help='Retain the selected-slot capability-refresh fix only after live modem voice verification')
    args=parser.parse_args()
    if args.run and args.install_refresh:parser.error('Choose one operation')
    if args.run or args.install_refresh:run(args.install_refresh)
    else:parser.print_help()
