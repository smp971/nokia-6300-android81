"""Update only the stock DeskClock APK, preserving app data and a verified original."""
from pathlib import Path
import argparse,hashlib,json,re,shlex,subprocess
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--restore',action='store_true');args=p.parse_args()
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def run(*a):return subprocess.check_output(['python','-X','utf8',str(root/'framework-command.py'),*a],timeout=30).decode(errors='replace').strip()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in run('/system/bin/cat','/proc/cmdline')
states=re.findall(r'mCallState=(\d+)',run('/system/bin/dumpsys','telephony.registry'));assert states and set(states)=={'0'},'Finish all calls before updating'
installed=run('/system/bin/sh','/system/bin/pm','path','com.android.deskclock').splitlines()[0].removeprefix('package:');current=run('/system/bin/sha256sum',installed).split()[0]
backup=root/'output/deskclock-background-before';backup.mkdir(exist_ok=True);index=backup/'manifest.json'
if not index.exists():
 assert not args.restore
 original=backup/'DeskClock.apk';run('/system/bin/cp',installed,'/tmp/leo-deskclock-original.apk');subprocess.run(adb+['pull','/tmp/leo-deskclock-original.apk',str(original)],capture_output=True,check=True);assert sha(original)==current
 record={'before':current,'after':None,'apk':str(original)};index.write_text(json.dumps(record,indent=2))
record=json.loads(index.read_text());assert current in (record['before'],record['after']),'Unexpected DeskClock APK version'
source=Path(record['apk']) if args.restore else root/'output/deskclock-background-stop.apk';expected=sha(source)
subprocess.run(adb+['push',str(source),'/tmp/leo-deskclock-update.apk'],capture_output=True,check=True);assert run('/system/bin/sha256sum','/tmp/leo-deskclock-update.apk').split()[0]==expected
if not args.restore:record['after']=expected;index.write_text(json.dumps(record,indent=2))
result=run('/system/bin/sh','/system/bin/pm','install','-r','/tmp/leo-deskclock-update.apk');assert 'Success' in result,result
installed=run('/system/bin/sh','/system/bin/pm','path','com.android.deskclock').splitlines()[0].removeprefix('package:');assert run('/system/bin/sha256sum',installed).split()[0]==expected
print('DeskClock restored; data retained' if args.restore else 'DeskClock background STOP fix installed and hash verified; data retained')
