"""Physical-key IME probes in the private, offline test editor. Never sends or saves text."""
from pathlib import Path
import subprocess,shlex,time,re,json,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def native(*args):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*args])],timeout=25).decode(errors='replace')
def java(*args):
    return subprocess.check_output(['python',str(ROOT/'framework-command.py'),'/system/bin/sh',*args],timeout=40).decode(errors='replace')
def key(*codes): java('/system/bin/input','keyevent',*map(str,codes))
assert 'androidboot.serialno=695d' in native('/system/bin/cat','/proc/cmdline')
assert all(s=='0' for s in re.findall(r'mCallState=(\d+)',native('/system/bin/dumpsys','telephony.registry')))
java('/system/bin/ime','set','ro.leo.keypad/.KeypadIme')
java('/system/bin/am','start','-W','-n','ro.leo.keypad/.EditorTestActivity')
def field(name='text'):
    java('/system/bin/am','start','-W','-n','ro.leo.keypad/.EditorTestActivity','--es','field',name)
def mode(target):
    try:
        tree=ET.fromstring(native('/system/bin/cat','/data/data/ro.leo.keypad/shared_prefs/keypad.xml'))
        item=tree.find("int[@name='mode']");current=int(item.attrib['value']) if item is not None else 0
    except (ET.ParseError,subprocess.CalledProcessError): current=0
    for i in range((target-current)%5): key(18)
def expect(name,value):
    time.sleep(.25)
    java('/system/bin/am','start','-W','-n','ro.leo.keypad/.EditorTestActivity','--es','test_id',name,'--es','expect',value)
    log=native('/system/bin/logcat','-d','-s','LeoImeTest:I','*:S')
    ok=('case='+name+' passed=true') in log
    print(name,'PASS' if ok else 'FAIL',flush=True);results[name]=ok
results={}
field();mode(3);key(14,9,12,15,15,7);expect('t9-salut','salut ')
field();mode(3);key(14,9,12,15,15,67,15,7);expect('t9-backspace','salut ')
field();mode(3);key(14,9,12,15,15,23);expect('t9-accept-ok','salut')
field();mode(4);key(14,9,12,15,15,7);expect('t9-uppercase','SALUT ')
field();mode(3);key(10,9,17,7);expect('t9-next-candidate','ea ')
field();mode(3);key(*([16]*10+[7]));expect('t9-unknown-fallback','9999999999 ')
field();mode(2);key(8,9,10);expect('numeric-mode','123')
field();mode(0);key(9,9,9);time.sleep(1);expect('multitap-preserved','c')
field();mode(3);field('password');key(9);time.sleep(1);expect('password-no-prediction','a')
field('numeric');key(8,9,10);expect('phone-field-numeric','123')
field();mode(0)
java('/system/bin/am','force-stop','ro.leo.keypad')
java('/system/bin/ime','set','ro.leo.keypad/.KeypadIme')
java('/system/bin/am','start','-W','-n','ro.leo.visual/.MainActivity')
(ROOT/'output/keypad-prediction-verified.json').write_text(json.dumps(results,indent=2))
assert all(results.values()),'Review IME probe failures before publishing'
