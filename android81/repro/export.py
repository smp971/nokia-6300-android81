"""Export audited local AOSP source changes, without binaries or credentials.

Input is the reviewed per-project source audit saved under ignored output/.
Run on Linux/WSL. No phone operations are performed.
"""
from pathlib import Path
import argparse,hashlib,json,re,shutil,subprocess,xml.etree.ElementTree as E

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,default=Path('/root/android81-leo/src'))
parser.add_argument('--audit',type=Path,default=root.parent/'output/source-audit.json')
parser.add_argument('--private-values',type=Path,help='Optional local-only file containing values to forbid, one per line')
args=parser.parse_args();src=args.source.resolve()
private_values=[line.strip() for line in args.private_values.read_bytes().splitlines() if line.strip()] if args.private_values else []
manifest=E.parse(root/'aosp-manifest.xml')
revisions={p.get('path',p.attrib['name']):p.attrib['revision'] for p in manifest.getroot().findall('project')}
allowed={'.java','.c','.cc','.cpp','.h','.hpp','.mk','.bp','.xml','.rc','.py','.sh','.aidl','.proto','.te','.conf','.txt','.json','.properties','.S','.map','.def','.inc','.cfg'}
special={'Makefile','Android.mk','Android.bp','NOTICE','LICENSE','.gitignore'}
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def checked(path):
    if path.is_symlink() or not path.is_file():raise SystemExit('Not a plain source file: '+str(path))
    if path.suffix not in allowed and path.name not in special:raise SystemExit('Review non-source file before export: '+str(path))
    data=path.read_bytes()
    if b'\x00' in data or b'PRIVATE KEY-----' in data or any(value in data for value in private_values):raise SystemExit('Private/binary content refused: '+str(path))
    data.decode('utf-8')
projects=[]
for row in json.loads(args.audit.read_text()):
    name=row['path'];target=src/name
    assert src in target.resolve().parents and name in revisions
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=target,text=True).strip();assert head==revisions[name]
    entry={'path':name,'revision':head,'files':[]}
    if row['changed']:
        stats=subprocess.check_output(['git','diff','--numstat','HEAD'],cwd=target,text=True)
        assert not any(line.startswith('-\t') for line in stats.splitlines()),'Binary change: '+name
        patch=subprocess.check_output(['git','diff','--no-ext-diff','--full-index','HEAD'],cwd=target)
        assert b'PRIVATE KEY-----' not in patch and not any(value in patch for value in private_values)
        destination=root/'patches'/(name.replace('/','__')+'.patch');destination.parent.mkdir(exist_ok=True);destination.write_bytes(patch)
        entry.update(patch=destination.relative_to(root).as_posix(),sha256=digest(destination))
    for relative in row['untracked']:
        # Backup copies are not part of the build; never publish private keys/blobs.
        if any(marker in relative for marker in ('.leo-before','.leo-original','.leo-host-original','.leo.bak','.before-leo','.bak','.orig')):continue
        original=target/relative;assert target.resolve() in original.resolve().parents
        checked(original)
        destination=root/'files'/name/relative;destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(original,destination)
        entry['files'].append({'path':relative,'source':destination.relative_to(root).as_posix(),'sha256':digest(destination),'mode':original.stat().st_mode&0o777})
    projects.append(entry)
index={'base':'AOSP android-8.1.0_r81','manifest_sha256':digest(root/'aosp-manifest.xml'),'projects':projects}
(root/'sources.json').write_text(json.dumps(index,indent=2)+'\n')
print('Exported',len(projects),'changed projects and',sum(len(p['files']) for p in projects),'additional source files')
