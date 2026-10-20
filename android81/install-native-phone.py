"""Install/restore the native Leo phone UI and optional default-dialer role. No flashing."""
from pathlib import Path
import argparse,json,subprocess,re,hashlib
root=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--activate',action='store_true');p.add_argument('--restore',action='store_true');args=p.parse_args();assert not(args.activate and args.restore)
adb=['D:/ROMS/platform-tools/adb.exe','-s','695d']
def run(*a):return subprocess.check_output(['python','-X','utf8',str(root/'framework-command.py'),*a],timeout=45).decode(errors='replace').strip()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert 'androidboot.serialno=695d' in run('/system/bin/cat','/proc/cmdline')
states=re.findall(r'mCallState=(\d+)',run('/system/bin/dumpsys','telephony.registry'));assert states and set(states)=={'0'},'Finish calls before installing'
folder=root/'output/native-phone-before';folder.mkdir(exist_ok=True);index=folder/'manifest.json'
current=run('/system/bin/sh','/system/bin/pm','path','ro.leo.visual').splitlines()[0][8:]
if not index.exists():
 assert not args.restore
 backup=folder/'LeoVisual.apk';run('/system/bin/cp',current,'/tmp/leo-before-native.apk');subprocess.run(adb+['pull','/tmp/leo-before-native.apk',str(backup)],check=True,capture_output=True)
 index.write_text(json.dumps({'apk':str(backup),'sha256':sha(backup),'dialer':run('/system/bin/sh','/system/bin/settings','get','secure','dialer_default_application')},indent=2))
record=json.loads(index.read_text());source=Path(record['apk']) if args.restore else root/'output/leo-visual.apk';expected=sha(source)
if args.restore:assert expected==record['sha256']
if run('/system/bin/sha256sum',current).split()[0]!=expected:
 subprocess.run(adb+['push',str(source),'/tmp/leo-native-phone.apk'],check=True,capture_output=True)
 assert 'Success' in run('/system/bin/sh','/system/bin/pm','install','-r','/tmp/leo-native-phone.apk')
if args.activate:run('/system/bin/sh','/system/bin/settings','put','secure','dialer_default_application','ro.leo.visual')
if args.restore:
 if record['dialer']=='null':run('/system/bin/sh','/system/bin/settings','delete','secure','dialer_default_application')
 else:run('/system/bin/sh','/system/bin/settings','put','secure','dialer_default_application',record['dialer'])
installed=run('/system/bin/sh','/system/bin/pm','path','ro.leo.visual').splitlines()[0][8:];assert run('/system/bin/sha256sum',installed).split()[0]==expected
print('Native phone restored' if args.restore else 'Native phone installed; default role '+('set to Leo' if args.activate else 'unchanged'))
run('/system/bin/sh','/system/bin/am','start','-W','-f','0x14000000','-n','ro.leo.visual/.PhoneActivity')
