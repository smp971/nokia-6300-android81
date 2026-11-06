"""Audit external DEX dependency coverage in an incremental Leo Visual APK."""
from pathlib import Path
import argparse,re,struct,zipfile

def classes(apk):
    result=set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r'classes[0-9]*\.dex',name):continue
            data=archive.read(name)
            if not data.startswith(b'dex\n'):raise ValueError('Invalid DEX header')
            strings=struct.unpack_from('<I',data,60)[0];types=struct.unpack_from('<I',data,68)[0]
            count,start=struct.unpack_from('<II',data,96)
            for i in range(count):
                cls=struct.unpack_from('<I',data,start+i*32)[0]
                string=struct.unpack_from('<I',data,types+cls*4)[0]
                offset=struct.unpack_from('<I',data,strings+string*4)[0]
                while data[offset]&128:offset+=1
                offset+=1
                descriptor=data[offset:data.index(b'\0',offset)]
                if descriptor in result:raise ValueError('Duplicate class: '+repr(descriptor))
                result.add(descriptor)
    if not result:raise ValueError('No classes found')
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('baseline',type=Path);p.add_argument('candidate',type=Path);p.add_argument('--allow-prefix',action='append',default=[],help='Explicit package path for intentionally added dependency classes');a=p.parse_args()
    original=classes(a.baseline);candidate=classes(a.candidate)
    before={n for n in original if not n.startswith(b'Lro/leo/')};after={n for n in candidate if not n.startswith(b'Lro/leo/')}
    if not before:raise SystemExit('Baseline contains no external dependencies')
    missing=before-after;extra=after-before
    prefixes=[]
    for value in a.allow_prefix:
        if not re.fullmatch(r"[A-Za-z0-9_]+(?:/[A-Za-z0-9_]+)+/?",value):raise ValueError("Invalid allowed dependency prefix")
        prefixes.append(("L"+value.rstrip("/")+"/").encode())
    unexpected={name for name in extra if not any(name.startswith(prefix) for prefix in prefixes)}
    if missing or unexpected:
        print('Missing dependency classes:',len(missing));print('Unexpected dependency classes:',len(unexpected));raise SystemExit(1)
    print('PASS:',len(candidate),'candidate classes;',len(before),'baseline external dependency classes preserved;',len(extra),'approved added classes; no duplicates')
if __name__=='__main__':main()
