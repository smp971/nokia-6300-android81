"""Count idle Now Playing redraws without starting playback or changing settings."""
from pathlib import Path
import subprocess,time,re,json,sys
root=Path(__file__).resolve().parent
label=sys.argv[1];assert label.replace('-','').isalnum()
def run(*a):return subprocess.check_output(['python','-X','utf8',str(root/'framework-command.py'),*a],timeout=30).decode(errors='replace')
run('/system/bin/input','keyevent','224')
run('/system/bin/sh','/system/bin/am','start','-W','-f','0x14000000','-n','ro.leo.visual/.NowPlayingActivity')
time.sleep(1)
run('/system/bin/dumpsys','gfxinfo','ro.leo.visual','reset')
time.sleep(10)
s=run('/system/bin/dumpsys','gfxinfo','ro.leo.visual')
count=re.search(r'Total frames rendered:\s*(\d+)',s)
assert count,'Frame count unavailable'
result={'idle_seconds':10,'frames':int(count.group(1)),'note':'Now Playing visible; no playback started. Not a battery-life measurement.'}
(root/'output'/('idle-page-'+label+'.json')).write_text(json.dumps(result,indent=2));print(json.dumps(result))
