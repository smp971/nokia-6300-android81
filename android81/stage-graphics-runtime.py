"""Host: stage graphics into RAM and execute real allocator/EGL probe."""
from pathlib import Path
import hashlib,json,subprocess
HERE=Path(__file__).resolve().parent;OUT=HERE/'output'
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d'];BB='/system/bin/busybox '
def call(*args):
    r=subprocess.run(ADB+list(args),capture_output=True,text=True,timeout=60)
    if r.returncode:raise RuntimeError(r.stderr or r.stdout)
    return r.stdout.replace('\r','')
assert 'uid=0' in call('shell',BB+'id')
assert call('shell',BB+'cat /tmp/leo-stage').strip()=='android-init-boot-action'
assert 'tmpfs /tmp tmpfs' in call('shell',BB+'cat /proc/mounts')
processes=call('shell',BB+'ps')
for line in processes.splitlines():
    fields=line.split(None,3)
    if len(fields)!=4:continue
    command=fields[3]
    if command.startswith('{'):command=command.split('} ',1)[-1]
    if command.startswith(('/system/bin/surfaceflinger','/system/bin/hwservicemanager','/vendor/bin/hw/android.hardware.graphics.')):
        raise RuntimeError('Graphics services are active: do not overwrite their mapped runtime. Reboot recovery before staging again.')
meta=json.loads((OUT/'graphics-runtime.json').read_text());archive=OUT/'graphics-runtime.tar.gz'
assert hashlib.sha256(archive.read_bytes()).hexdigest()==meta['sha256']
call('push',str(archive),'/tmp/leo-graphics-runtime.tar.gz')
call('shell',BB+'mkdir -p /tmp/leo-graphics && '+BB+'tar -xzf /tmp/leo-graphics-runtime.tar.gz -C /tmp/leo-graphics')
helper=OUT/'leo-graphics-exec'
call('push',str(helper),'/tmp/leo-graphics-exec');call('shell',BB+'chmod 755 /tmp/leo-graphics-exec')
hardware=call('shell','/tmp/leo-graphics-exec /tmp/leo-graphics /system/bin/getprop ro.hardware').strip()
print('Property namespace ro.hardware:',hardware,flush=True)
assert hardware=='qcom','Missing Android property contexts in graphics runtime'
ready=call('shell','/tmp/leo-graphics-exec /tmp/leo-graphics /system/bin/getprop hwservicemanager.ready').strip()
if ready!='true':
    print(call('shell','/tmp/leo-graphics-exec /tmp/leo-graphics /system/bin/hwservicemanager > /tmp/leo-hwservicemanager.log 2>&1 < /dev/null & echo $!'),flush=True)
status=call('shell','/tmp/leo-graphics-exec /tmp/leo-graphics /system/bin/leo_graphics_probe > /tmp/leo-graphics-probe.log 2>&1; echo $?').strip()
call('pull','/tmp/leo-graphics-probe.log',str(OUT/'graphics-probe.log'))
log=(OUT/'graphics-probe.log').read_text(errors='replace');print(log)
result={'exit_status':status,'probe_success':status=='0' and 'LEO_GRAPHICS_PROBE_OK' in log,
        'runtime_archive_sha256':meta['sha256'],'root_directory':'/tmp/leo-graphics',
        'persistent_partition_writes':False,'graphics_display_taken_over':False}
(OUT/'graphics-probe-result.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result,indent=2))
