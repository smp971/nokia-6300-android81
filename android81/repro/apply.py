"""Verify and apply the published AOSP source snapshot; never builds or flashes."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,default=Path('/root/android81-leo/src'))
parser.add_argument('--check-only',action='store_true')
args=parser.parse_args();source=args.source.resolve()
index=json.loads((root/'sources.json').read_text())
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
assert sha(root/'aosp-manifest.xml')==index['manifest_sha256'],'Manifest differs from snapshot'
def inside(base,relative):
    candidate=(base/relative).resolve()
    if candidate!=base and base not in candidate.parents:raise SystemExit('Path outside expected root: '+relative)
    return candidate
patches=[];files=[]
for project in index['projects']:
    target=inside(source,project['path'])
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=target,text=True).strip()
    if head!=project['revision']:raise SystemExit('Wrong revision: '+project['path'])
    if project.get('patch'):
        patch=inside(root,project['patch']);assert sha(patch)==project['sha256']
        fresh=subprocess.run(['git','apply','--check',str(patch)],cwd=target,capture_output=True)
        if fresh.returncode==0:patches.append((target,patch))
        else:
            applied=subprocess.run(['git','apply','--reverse','--check',str(patch)],cwd=target,capture_output=True)
            if applied.returncode:raise SystemExit('Local changes conflict with patch: '+project['path'])
    for entry in project.get('files',[]):
        original=inside(root,entry['source']);destination=inside(target,entry['path']);assert sha(original)==entry['sha256']
        if destination.exists() and sha(destination)!=entry['sha256']:raise SystemExit('Different existing file: '+str(destination))
        if not destination.exists():files.append((original,destination,entry['mode']))
print('Verified snapshot:',len(index['projects']),'changed AOSP projects;',len(patches),'patches and',len(files),'new files pending')
if args.check_only:raise SystemExit(0)
for target,patch in patches:subprocess.run(['git','apply',str(patch)],cwd=target,check=True)
for original,destination,mode in files:
    destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,destination);destination.chmod(mode)
print('AOSP changes applied; device sources are staged separately by stage-device.sh')
