"""Stop only this temporary IMS port trial and restore its saved runtime settings."""
from pathlib import Path
import importlib.util,re,subprocess,sys,time
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('radio',root/'install-radio-runtime.py')
radio=importlib.util.module_from_spec(spec);spec.loader.exec_module(radio)
assert 'androidboot.serialno=695d' in radio.shell('/system/bin/cat','/proc/cmdline')
state=root/'output/ims-reference'
override=(state/'volte-override-before.txt').read_text(encoding='utf-8-sig').strip()
mode=(state/'socket-mode-before.txt').read_text(encoding='utf-8-sig').strip()
assert re.fullmatch('[0-7]{3,4}',mode)
calls=radio.shell('/system/bin/dumpsys','telephony.registry')
assert re.findall(r'^\s*mCallState=(\d+)',calls,re.M)==['0','0'],'A call is active or call state unavailable.'
radio.shell('/system/bin/setprop','persist.dbg.volte_avail_ovr',override)
def java(*args):
    subprocess.run([sys.executable,str(root/'framework-command.py'),'/system/bin/sh','/system/bin/am',*args],check=True,timeout=30)
try:pid=radio.shell('/system/bin/pidof','org.codeaurora.ims:leo_ims')
except RuntimeError:pid=''
if pid:
    java('startservice','-n','org.codeaurora.ims/.ImsService','--ez','leoReconfigure','true')
    for _ in range(10):
        dump=radio.shell('/system/bin/dumpsys','activity','service','org.codeaurora.ims/.ImsService')
        if 'registration=2' in dump:break
        time.sleep(1)
    else:raise SystemExit('IMS did not deregister; trial process left intact for inspection.')
    java('force-stop','org.codeaurora.ims')
for name in ('imsqmidaemon','imsdatadaemon'):
    try:pids=radio.shell('/system/bin/pidof',name).split()
    except RuntimeError:pids=[]
    for pid in pids:
        command=radio.shell('/system/bin/cat','/proc/'+pid+'/cmdline').split('\0')[0]
        assert command=='/tmp/leo-ims/'+name,'Refusing to stop a daemon outside this trial.'
        radio.shell('/system/bin/kill','-TERM',pid)
for _ in range(5):
    if radio.shell('/system/bin/sh','-c','test ! -e /dev/socket/ims_qmid && test ! -e /dev/socket/ims_datad; echo $?')=='0':break
    time.sleep(1)
else:raise SystemExit('Temporary control sockets still present; inspect trial parent processes.')
radio.shell('/system/bin/setprop','sys.ims.QMI_DAEMON_STATUS','')
radio.shell('/system/bin/setprop','sys.ims.DATA_DAEMON_STATUS','')
radio.shell('/system/bin/chmod',mode,'/dev/socket/qmux_radio/rild_ims0')
print('Temporary IMS trial stopped; override and socket mode restored. Ported APK remains installed, inactive.')
