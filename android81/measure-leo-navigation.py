"""Bounded Home/grid navigation sample. No calls, messages or setting changes."""
from pathlib import Path
import json,subprocess,time,sys
root=Path(__file__).resolve().parent
label=sys.argv[1] if len(sys.argv)>1 else 'sample'
assert label.replace('-','').isalnum()
def run(*args):
 return subprocess.check_output(['python','-X','utf8',str(root/'framework-command.py'),*args],timeout=30).decode(errors='replace')
run('/system/bin/input','keyevent','224')
run('/system/bin/sh','/system/bin/am','start','-f','0x14000000','-n','ro.leo.visual/.MainActivity')
run('/system/bin/dumpsys','gfxinfo','ro.leo.visual','reset')
run('/system/bin/input','keyevent','23')
rounds=int(sys.argv[2]) if len(sys.argv)>2 else 1
assert 1<=rounds<=5
for k in [22,22,20,21,19,22,20,21,19,20,22,21]*rounds:
 run('/system/bin/input','keyevent',str(k));time.sleep(.08)
s=run('/system/bin/dumpsys','gfxinfo','ro.leo.visual','framestats')
frames=[];columns=None
for line in s.splitlines():
 row=line.strip().split(',')
 if 'IntendedVsync' in row and 'FrameCompleted' in row:columns=row;continue
 if columns and row and row[0].isdigit() and len(row)>=len(columns)-1:
  try:
   if int(row[0])!=0:continue
   start=int(row[columns.index('IntendedVsync')]);end=int(row[columns.index('FrameCompleted')]);ms=(end-start)/1e6
   if 0<ms<10000:frames.append(ms)
  except (ValueError,IndexError):pass
frames.sort();result={'frames':len(frames),'note':'Android frame timings during ADB grid navigation; not physical key-to-display latency.'}
if frames:result.update(p50_ms=round(frames[len(frames)//2],2),p95_ms=round(frames[min(len(frames)-1,len(frames)*95//100)],2))
(root/'output'/('navigation-'+label+'.json')).write_text(json.dumps(result,indent=2))
print(json.dumps(result))
run('/system/bin/sh','/system/bin/am','start','-f','0x14000000','-n','ro.leo.visual/.MainActivity')
