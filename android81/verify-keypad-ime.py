"""Physical-input tests in a private local editor; no calls, messaging or text logs."""
from pathlib import Path
import shlex
import struct
import subprocess
import time
from PIL import Image

ROOT=Path(__file__).resolve().parent
ADB=['D:/ROMS/platform-tools/adb.exe','-s','695d']
OUT=ROOT/'output/keypad-ime-test'
OUT.mkdir(parents=True,exist_ok=True)

def java(*args):
    return subprocess.check_output(['python',str(ROOT/'framework-command.py'),'/system/bin/sh',*args],timeout=45).decode('utf-8',errors='replace')

def native(*args):
    return subprocess.check_output(ADB+['shell',shlex.join(['/sbin/leo-framework-exec',*args])],timeout=45).decode('utf-8',errors='replace')

def editor(*args):
    return java('/system/bin/am','start','-W','-n','ro.leo.keypad/.EditorTestActivity',*args)

def pulse(scans):
    script=[]
    for code in scans:
        if code is None:
            script.append('sleep 1.1')
        else:
            script.extend([f'sendevent /dev/input/event1 1 {code} 1','sendevent /dev/input/event1 0 0 0',
                           f'sendevent /dev/input/event1 1 {code} 0','sendevent /dev/input/event1 0 0 0','sleep 0.08'])
    native('/system/bin/sh','-c','\n'.join(script))

def capture(name):
    native('/system/bin/screencap','/tmp/leo-ime.raw')
    raw=OUT/(name+'.raw')
    subprocess.run(ADB+['pull','/tmp/leo-ime.raw',str(raw)],check=True,stdout=subprocess.DEVNULL)
    data=raw.read_bytes(); w,h,fmt,_=struct.unpack('<4I',data[:16])
    assert fmt==1 and len(data[16:])==w*h*4
    Image.frombytes('RGBA',(w,h),data[16:]).save(OUT/(name+'.png'))

java('/system/bin/input','keyevent','224')
editor()
cases=[
    ('cycle','text',[3,3,3,352],'c'),
    ('separate-same-key','text',[3,106,3,352],'aa'),
    ('timeout','text',[3,None,3,352],'aa'),
    ('word','text',[8,8,8,8,106,3,106,6,6,6,106,9,9,106,9,352],'salut'),
    ('romanian','text',[3,3,3,3,106,3,3,3,3,3,106,5,5,5,5,106,8,8,8,8,8,106,9,9,9,9,352],'ăâîșț'),
    ('uppercase','text',[523,3,3,3,352],'C'),
    ('numeric-mode','text',[523,523,3,11,8],'207'),
    ('erase-pending','text',[3,3,158,4,352],'d'),
    ('erase-committed','text',[3,106,3,3,106,158],'a'),
    ('phone-passthrough','numeric',[3,3,3,523,522],'222#*'),
    ('phone-erase','numeric',[2,3,158],'1'),
]
for name,field,scans,expected in cases:
    editor('--es','field',field)
    time.sleep(.4)
    pulse(scans)
    time.sleep(.15)
    editor('--es','expect',expected,'--es','test_id',name)
    log=native('/system/bin/logcat','-d','-s','LeoImeTest:I','*:S')
    (OUT/'results.log').write_text(log,encoding='utf-8')
    records=[line for line in log.splitlines() if f'case={name} passed=' in line]
    assert records and records[-1].endswith('passed=true'), 'Failed: '+name
    print('PASS',name,flush=True)
    if name in ('word','romanian','phone-passthrough'): capture(name)
editor('--es','field','text')
capture('ready')
print('11 physical-key input cases passed; private editor left ready')
