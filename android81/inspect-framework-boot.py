"""Read-only boot evidence from the target, including init's separate mount namespace."""
from pathlib import Path
import json,subprocess
OUT=Path(__file__).resolve().parent/'output'
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
HELPER='/sbin/leo-framework-exec '
def shell(command):
    try:
        r=subprocess.run(ADB+['shell',command],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=25)
        return {'returncode':r.returncode,'stdout':r.stdout.replace('\r',''),'stderr':r.stderr}
    except subprocess.TimeoutExpired:return {'error':'timeout'}
identity=shell('/system/bin/busybox cat /proc/cmdline')
assert 'androidboot.serialno=695d' in identity.get('stdout','')
commands={
    'stage':'/system/bin/busybox cat /tmp/leo-stage /tmp/leo-framework-mount.log',
    'processes':'/system/bin/busybox ps',
    'mounts':HELPER+'/system/bin/cat /proc/mounts',
    'properties':HELPER+'/system/bin/getprop',
    'services':HELPER+'/system/bin/service list',
    'activity':HELPER+'/system/bin/dumpsys -t 5 activity activities',
    'window':HELPER+'/system/bin/dumpsys -t 5 window windows',
    'surfaceflinger':HELPER+'/system/bin/dumpsys -t 5 SurfaceFlinger',
    'logcat':HELPER+'/system/bin/logcat -d -t 500 -v threadtime',
    'kernel':'/system/bin/busybox dmesg',
}
report={'target_serial':'695d','read_only':True,'checks':{}}
for name,command in commands.items():
    result=shell(command);report['checks'][name]=result
    (OUT/('framework-boot-'+name+'.txt')).write_text(result.get('stdout','')+result.get('stderr',''),encoding='utf-8')
    print(name,result.get('returncode',result.get('error')),flush=True)
props=report['checks']['properties'].get('stdout','')
activity=report['checks']['activity'].get('stdout','')
processes=report['checks']['processes'].get('stdout','')
report.update(boot_completed='[sys.boot_completed]: [1]' in props,
    system_server_present='system_server' in processes,
    launcher_mentioned_in_activity_dump='com.android.launcher3' in activity)
(OUT/'framework-boot-result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='checks'},indent=2))
