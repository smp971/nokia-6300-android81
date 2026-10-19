"""Update only the two Leo APKs on serial 695d, retaining verified prior APKs locally."""
from pathlib import Path
import subprocess,shlex,re,json,hashlib,datetime
ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*args):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*args])],timeout=30).decode(errors='replace').strip()
def java(*args):
    return subprocess.check_output(['python',str(ROOT/'framework-command.py'),'/system/bin/sh',*args],timeout=90).decode(errors='replace').strip()
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
states=re.findall(r'mCallState=(\d+)',native('/system/bin/dumpsys','telephony.registry'))
assert states and all(x=='0' for x in states),'A call is active or state is unavailable'
assert 'ServiceRecord' not in native('/system/bin/dumpsys','activity','services','ro.leo.visual.AlarmRingService'),'An alarm is ringing'
folder=ROOT/'output/app-suite-before'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
folder.mkdir(parents=True,exist_ok=False)
packages={'ro.leo.visual':'leo-visual.apk','ro.leo.keypad':'leo-keypad-ime.apk'}
report={}
for package,apk in packages.items():
    assert (ROOT/'output'/apk).is_file()
    paths=[s[len('package:'):] for s in java('/system/bin/pm','path',package).splitlines() if s.startswith('package:')]
    assert len(paths)==1,'Expected an installed, unsplit Leo APK'
    before=folder/apk; staged='/tmp/leo-suite-before-'+apk
    expected=native('/system/bin/sha256sum',paths[0]).split()[0]
    native('/system/bin/cp',paths[0],staged)
    subprocess.run(ADB+['pull',staged,str(before)],check=True,stdout=subprocess.DEVNULL)
    assert hashlib.sha256(before.read_bytes()).hexdigest()==expected
    report[package]={'before':expected,'after':hashlib.sha256((ROOT/'output'/apk).read_bytes()).hexdigest()}
(folder/'manifest.json').write_text(json.dumps(report,indent=2))
for package,apk in packages.items():
    staged='/tmp/leo-suite-'+apk
    subprocess.run(ADB+['push',str(ROOT/'output'/apk),staged],check=True,stdout=subprocess.DEVNULL)
    result=java('/system/bin/pm','install','-r',staged)
    assert 'Success' in result,result
    print(package,'updated; original APK verified and retained',flush=True)
subprocess.run(['python',str(ROOT/'activate-visual-home.py')],check=True)
java('/system/bin/ime','enable','ro.leo.keypad/.KeypadIme')
java('/system/bin/ime','set','ro.leo.keypad/.KeypadIme')
print('Leo suite installed; boot/recovery and kernel drivers unchanged')
