"""Build a bounded, offline Leo HTML application package. No Android SDK required."""
from pathlib import Path
import argparse, hashlib, json, re, zipfile

def package(folder, output=None):
    folder=Path(folder).resolve()
    manifest=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
    app_id=manifest['id']; entry=manifest['entry']
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,39}',app_id) or app_id.startswith('stage-'):
        raise ValueError('Invalid application id')
    if not isinstance(manifest['name'],str) or not 1<=len(manifest['name'].strip())<=60:
        raise ValueError('Invalid name')
    def safe(name):
        return len(name)<160 and not name.startswith('/') and ':' not in name and chr(92) not in name and all(p not in ('','.','..') for p in name.split('/'))
    if not safe(entry) or not entry.endswith('.html') or not (folder/entry).is_file():
        raise ValueError('Entry must be a local HTML file')
    destination=Path(output) if output else folder.parent/(app_id+'.leoapp.zip')
    if destination.resolve().is_relative_to(folder):
        raise ValueError('Output must be outside the source folder')
    files=sorted(p for p in folder.rglob('*') if p.is_file())
    if not files or len(files)>64: raise ValueError('Maximum 64 files')
    total=0
    for file in files:
        if file.is_symlink() or not file.resolve().is_relative_to(folder) or not safe(file.relative_to(folder).as_posix()):
            raise ValueError('Unsafe source path')
        size=file.stat().st_size; total+=size
        if size>524288 or total>2097152: raise ValueError('File/package too large')
    destination.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(destination,'x',compression=zipfile.ZIP_DEFLATED) as archive:
        for file in files: archive.write(file,file.relative_to(folder).as_posix())
    print(destination)
    print('SHA-256:',hashlib.sha256(destination.read_bytes()).hexdigest())
    return destination

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder',type=Path);parser.add_argument('--output',type=Path)
    args=parser.parse_args();package(args.folder,args.output)
