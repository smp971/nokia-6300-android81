"""Guarded recovery update from the early ADB diagnostic mount namespace."""
from pathlib import Path
import argparse,hashlib,json,subprocess
parser=argparse.ArgumentParser()
parser.add_argument('--ui',action='store_true')
parser.add_argument('--graphics',action='store_true')
parser.add_argument('--framework',action='store_true')
parser.add_argument('--expected-current-md5',default='934a64586f3068b640e1876366fa3d74')
args=parser.parse_args()
here=Path(__file__).resolve().parent;out=here/'output'
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d'];bb='/system/bin/busybox '
def call(*args):
    r=subprocess.run(adb+list(args),capture_output=True,text=True,timeout=60)
    if r.returncode:raise RuntimeError(r.stderr or r.stdout)
    return r.stdout.replace('\r','').strip()
assert 'uid=0' in call('shell',bb+'id')
assert call('shell',bb+'cat /tmp/leo-stage') in ('bootstrap-ready','android-init-boot-action','android-framework-boot-action','framework-mount-failed')
cmd=call('shell',bb+'cat /proc/cmdline')
assert 'androidboot.serialno=695d' in cmd and 'androidboot.hw.name=leo' in cmd
parts={}
for name,partition in [('boot','mmcblk0p29'),('recovery','mmcblk0p33')]:
    info=call('shell',bb+'cat /sys/class/block/'+partition+'/uevent')
    values=dict(line.split('=',1) for line in info.splitlines() if '=' in line)
    assert values['PARTNAME']==name and values['DEVNAME']==partition
    assert int(call('shell',bb+'cat /sys/class/block/'+partition+'/size'))*512==33554432
    path='/dev/leo-verify-'+name
    # Device nodes are private volatile diagnostic /dev, never filesystem images.
    call('shell','if [ ! -b '+path+' ]; then '+bb+'mknod '+path+' b '+values['MAJOR']+' '+values['MINOR']+'; fi')
    assert call('shell',bb+'stat -c %t:%T '+path)==f"{int(values['MAJOR']):x}:{int(values['MINOR']):x}"
    parts[name]=path
saved=json.loads((here/'backup-live/checksums.json').read_text())
for name in ('boot','recovery'):
    assert hashlib.sha256((here/'backup-live'/f'{name}-device.img').read_bytes()).hexdigest()==saved[name]['sha256']
assert call('shell',bb+'md5sum '+parts['boot']).split()[0]==saved['boot']['md5']
previous=call('shell',bb+'md5sum '+parts['recovery']).split()[0]
assert previous==args.expected_current_md5
stem='recovery-android81-framework' if args.framework else 'recovery-android81-graphics' if args.graphics else 'recovery-android81-ui' if args.ui else 'recovery-init-diagnostic'
meta=json.loads((out/(stem+'.json')).read_text())
image=out/(stem+'.img')
assert hashlib.sha256(image.read_bytes()).hexdigest()==meta['sha256'] and image.stat().st_size==33554432
if args.framework:
    staged=json.loads((out/'framework-stage-result.json').read_text())
    assert staged['target_serial']=='695d' and staged['partition_formatting'] is False
    assert staged['images']['system.raw.img']['sha256']==meta['framework_system_image_sha256']
    assert 'data.raw.img' in staged['images']
    assert call('shell',bb+'cat /tmp/leo-sd/leo-android81/owner')=='LEO_ANDROID81_DIAGNOSTIC_695d'
    for name,expected_size in [('system.raw.img',838860800),('data.raw.img',536870912)]:
        assert int(call('shell',bb+'stat -c %s /tmp/leo-sd/leo-android81/'+name))==expected_size
call('push',str(image),'/tmp/leo-next-recovery.img')
assert call('shell',bb+'md5sum /tmp/leo-next-recovery.img').split()[0]==meta['md5']
helper=out/('leo-diag-bootstrap-framework' if args.framework else 'leo-diag-bootstrap')
call('push',str(helper),'/tmp/leo-reboot-helper')
assert call('shell',bb+'sha256sum /tmp/leo-reboot-helper').split()[0]==meta['bootstrap_sha256']
call('shell',bb+'chmod 755 /tmp/leo-reboot-helper')
state={'target_serial':'695d','previous_recovery_md5':previous,'probe_md5':meta['md5'],
       'boot_preserved':True,'system_written':False,'userdata_formatted':False,'retain_patched_recovery':True,
       'stage':'preflight-passed-from-early-adb'}
p=out/('framework-install.json' if args.framework else 'graphics-install.json' if args.graphics else 'ui-install.json' if args.ui else 'init-diagnostic-v5-install.json');p.write_text(json.dumps(state,indent=2))
print(call('shell',bb+'dd if=/tmp/leo-next-recovery.img of='+parts['recovery']+' bs=4096'))
call('shell',bb+'sync')
actual=call('shell',bb+'md5sum '+parts['recovery']).split()[0]
state.update(readback_md5=actual,stage='written-verified' if actual==meta['md5'] else 'write-verification-failed-no-reboot')
p.write_text(json.dumps(state,indent=2));assert actual==meta['md5']
print('Recovery verified; restarting directly to recovery using syscall (early ADB has a private /dev namespace).',flush=True)
state['stage']='rebooting-to-recovery';p.write_text(json.dumps(state,indent=2))
r=subprocess.run(adb+['shell','/tmp/leo-reboot-helper --reboot-recovery'],capture_output=True,text=True,timeout=15)
print(r.stdout,r.stderr)
