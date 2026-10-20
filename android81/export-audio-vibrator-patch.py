"""Export the audio vibrator adapter into the pinned AOSP reconstruction manifest."""
from pathlib import Path
import subprocess,json,hashlib
root=Path(__file__).resolve().parent;project='/root/android81-leo/src/hardware/interfaces'
def git(*a):return subprocess.check_output(['wsl','-d','kaios','--','git','-C',project,*a])
revision=git('rev-parse','HEAD').decode().strip();patch=git('diff','--','vibrator/1.0/default/Vibrator.cpp','vibrator/1.0/default/Android.bp');assert patch
target=root/'repro/patches/hardware__interfaces.patch';target.write_bytes(patch)
p=root/'repro/sources.json';data=json.loads(p.read_text());entry={'path':'hardware/interfaces','revision':revision,'files':[],'patch':'patches/hardware__interfaces.patch','sha256':hashlib.sha256(patch).hexdigest()}
data['projects']=[x for x in data['projects'] if x['path']!=entry['path']];data['projects'].append(entry);data['projects'].sort(key=lambda x:x['path']);p.write_text(json.dumps(data,indent=2)+'\n')
print('Exported pinned hardware/interfaces patch')
