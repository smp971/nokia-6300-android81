"""Inventory dirty AOSP projects and untracked files without changing the tree."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,json,subprocess

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,default=Path('/root/android81-leo/src'))
parser.add_argument('--workers',type=int,default=6)
args=parser.parse_args();source=args.source.resolve()
projects=(source/'.repo/project.list').read_text().splitlines()
def inspect(relative):
    directory=source/relative
    if not directory.is_dir():raise RuntimeError('Missing project: '+relative)
    # Standard mtime/size checks avoid rehashing pristine files for ctime/inode
    # differences introduced by moving a checkout between WSL environments.
    git=['git','-c','core.trustctime=false','-c','core.checkStat=minimal']
    changed=subprocess.check_output(git+['diff','--name-only','HEAD'],cwd=directory,text=True).splitlines()
    untracked=subprocess.check_output(git+['ls-files','--others','--exclude-standard'],cwd=directory,text=True).splitlines()
    if changed or untracked:return {'path':relative,'changed':changed,'untracked':untracked}
with ThreadPoolExecutor(max_workers=max(1,min(8,args.workers))) as pool:
    result=[value for value in pool.map(inspect,projects) if value]
output=root.parent/'output/source-audit.json';output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(result,indent=2)+'\n')
for row in result:print(row['path'],len(row['changed']),'modified;',len(row['untracked']),'untracked')
print('Review the inventory before running export.py; private/output files are not automatically approved.')
