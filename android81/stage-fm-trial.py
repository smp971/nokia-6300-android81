"""Stage original FM utility in a private trial directory; no radio enable or boot edits.
The validated isolated modem runtime supplies shared QMI dependencies.
"""
from pathlib import Path
import hashlib, json, shlex, subprocess
ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
BASE='/data/leo-fm'
LIBPATH=BASE+'/lib:/system/vendor/leo-radio/lib:/system/lib'
def native(*args):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*args])],timeout=25).decode(errors='replace').strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert native('/system/bin/getprop','sys.boot_completed')=='1'
native('/system/bin/mkdir','-p',BASE+'/lib')
native('/system/bin/chmod','700',BASE,BASE+'/lib')
files={'fm_qsoc_patches':ROOT.parent/'analysis/system/bin/fm_qsoc_patches','lib/libqcci_legacy.so':ROOT.parent/'analysis/system/vendor/lib/libqcci_legacy.so','trial':ROOT/'output/leo-fm-trial','diagnostic':ROOT/'output/leo-fm-diagnostic','trace.so':ROOT/'output/leo-fm-trace.so','audio.jar':ROOT/'output/leo-fm-audio.jar','route':ROOT/'output/leo-fm-route'}
report={}
for relative,path in files.items():
    expected=digest(path)
    subprocess.run(ADB+['push',str(path),'/tmp/leo-fm-transfer'],capture_output=True,check=True,timeout=25)
    assert native('/system/bin/sha256sum','/tmp/leo-fm-transfer').split()[0]==expected
    native('/system/bin/cp','/tmp/leo-fm-transfer',BASE+'/'+relative+'.new')
    native('/system/bin/chmod','600' if relative.endswith('.so') else '700',BASE+'/'+relative+'.new')
    assert native('/system/bin/sha256sum',BASE+'/'+relative+'.new').split()[0]==expected
    native('/system/bin/mv',BASE+'/'+relative+'.new',BASE+'/'+relative)
    report[relative]=expected
try:
    result=native('/system/bin/env','LD_LIBRARY_PATH='+LIBPATH,BASE+'/fm_qsoc_patches')
except subprocess.CalledProcessError as failure:
    if failure.returncode!=255:raise
    result=failure.output.decode(errors='replace').strip()
assert result.startswith("USAGE:"),result
print("FM utility and dependencies loaded; usage-only probe passed")
(ROOT/'output/fm-staged.json').write_text(json.dumps({'sha256':report,'linker':result,'radio_enabled':False},indent=2))
print('FM utility staged; no initialization or system/boot changes performed')
