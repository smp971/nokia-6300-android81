"""Explicit Nokia 6300 recovery-only test; preserves normal boot/system/userdata."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess

HERE=Path(__file__).resolve().parent
ADB=Path('D:/ROMS/platform-tools/adb.exe')
SERIAL='695d'
parser=argparse.ArgumentParser()
parser.add_argument('--manual-recovery',action='store_true',help='Write and verify only; user enters recovery with keys')
parser.add_argument('--control',action='store_true',help='Use no-content-change recovery repack control')
parser.add_argument('--shell',action='store_true',help='Use stock recovery plus static diagnostic shell')
parser.add_argument('--debug',action='store_true',help='Use recovery with known-working root adbd and diagnostic domain policy')
parser.add_argument('--init-diagnostic',action='store_true',help='Early ADB gate before instrumented Android init')
parser.add_argument('--from-debug-recovery',action='store_true',help='Require root-adb-v1 marker and model in recovery properties')
parser.add_argument('--expected-current-recovery-md5',help='Verified hash of the previous installed probe; otherwise require original recovery')
args=parser.parse_args()
assert sum((args.control,args.shell,args.debug,args.init_diagnostic))<=1
LOG=HERE/'output'/('init-diagnostic-install.json' if args.init_diagnostic else 'debug-install.json' if args.debug else 'shell-install.json' if args.shell else 'control-install.json' if args.control else 'hardware-test.json')
def adb(*args,timeout=45):
    r=subprocess.run([str(ADB),'-s',SERIAL,*args],capture_output=True,text=True,timeout=timeout)
    if r.returncode:
        raise RuntimeError(r.stderr.strip() or r.stdout.strip())
    return r.stdout.strip()
expected=json.loads((HERE/'output'/('recovery-init-diagnostic.json' if args.init_diagnostic else 'recovery-debug.json' if args.debug else 'recovery-shell.json' if args.shell else 'recovery-control.json' if args.control else 'recovery-probe-checksums.json')).read_text())
remote_image='/tmp/leo-recovery-init-diagnostic.img' if args.init_diagnostic else '/data/local/tmp/leo-recovery-debug.img' if args.debug else '/data/local/tmp/leo-recovery-shell.img' if args.shell else '/data/local/tmp/leo-recovery-control.img' if args.control else '/data/local/tmp/leo-recovery-probe.img'
if args.init_diagnostic and not args.from_debug_recovery:
    remote_image='/data/local/tmp/leo-recovery-init-diagnostic.img'
saved=json.loads((HERE/'backup-live/checksums.json').read_text())
backup=HERE/'backup-live/recovery-device.img'
assert hashlib.sha256(backup.read_bytes()).hexdigest()==saved['recovery']['sha256']
if args.from_debug_recovery:
    assert adb('shell','cat /tmp/leo-debug-init')=='root-adb-v1'
    assert 'ro.product.model=Nokia 6300 4G' in adb('shell','cat /default.prop')
else:
    assert adb('shell','getprop ro.product.model')=='Nokia 6300 4G'
assert 'uid=0' in adb('shell','id')
bb='/system/bin/busybox ' if args.from_debug_recovery else ''
assert adb('shell','md5sum /dev/block/bootdevice/by-name/boot').split()[0]==saved['boot']['md5']
current_md5=adb('shell','md5sum /dev/block/bootdevice/by-name/recovery').split()[0]
assert current_md5==(args.expected_current_recovery_md5 or saved['recovery']['md5'])
assert adb('shell','md5sum '+remote_image).split()[0]==expected['md5']
assert int(adb('shell',bb+'blockdev --getsize64 /dev/block/bootdevice/by-name/recovery'))==expected['bytes']
state={'target_serial':SERIAL,'partition_written':'recovery','normal_boot_preserved':True,
       'system_written':False,'userdata_formatted':False,'probe_md5':expected['md5'],
       'restore_recovery_md5':saved['recovery']['md5'],'previous_recovery_md5':current_md5,
       'retain_patched_recovery':True,'stage':'preflight-passed'}
LOG.write_text(json.dumps(state,indent=2))
print(adb('shell',bb+'dd if='+remote_image+' of=/dev/block/bootdevice/by-name/recovery bs=4096'))
adb('shell',bb+'sync')
readback=adb('shell','md5sum /dev/block/bootdevice/by-name/recovery').split()[0]
state.update(stage='written',readback_md5=readback)
LOG.write_text(json.dumps(state,indent=2))
if readback!=expected['md5']:
    state.update(stage='write-verification-failed-no-reboot')
    LOG.write_text(json.dumps(state,indent=2))
    raise RuntimeError('Probe write verification failed; no reboot or automatic restoration; inspect before continuing')
state['stage']='awaiting-manual-recovery-entry' if args.manual_recovery else 'rebooting-into-recovery-probe'
LOG.write_text(json.dumps(state,indent=2))
if args.manual_recovery:
    print('Recovery write verified. No reboot command sent; awaiting user key combination.')
else:
    print('Recovery write verified. Rebooting to probe; normal boot partition unchanged.')
    print(adb('reboot','recovery'))
