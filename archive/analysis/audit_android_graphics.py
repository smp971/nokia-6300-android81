"""Inventory ELF32 dependencies of stock display libraries without executing them."""
from pathlib import Path
import json
import struct

ROOT=Path(__file__).resolve().parent.parent
SYSTEM=ROOT/'analysis/system'
OUT=ROOT/'android81/graphics-dependencies.json'

def dynamic(path):
    data=path.read_bytes()
    if data[:6] != b'\x7fELF\x01\x01':
        raise ValueError(f'Not a little-endian ELF32: {path}')
    header=struct.unpack_from('<16sHHIIIIIHHHHHH',data)
    phoff,phentsize,phnum=header[5],header[9],header[10]
    ph=[struct.unpack_from('<8I',data,phoff+i*phentsize) for i in range(phnum)]
    tags=[]
    for p in ph:
        if p[0] == 2:
            for off in range(p[1],p[1]+p[4],8):
                tag,value=struct.unpack_from('<II',data,off)
                if tag==0:
                    break
                tags.append((tag,value))
    straddr=next((v for t,v in tags if t==5),None)
    if straddr is None:
        return []
    segment=next(p for p in ph if p[0]==1 and p[2]<=straddr<p[2]+p[4])
    stroff=segment[1]+straddr-segment[2]
    def string(off):
        end=data.index(b'\0',stroff+off)
        return data[stroff+off:end].decode()
    return [string(v) for t,v in tags if t==1]

starts=[SYSTEM/'lib/hw/gralloc.msm8909.so',SYSTEM/'lib/hw/hwcomposer.msm8909.so']
starts+=list((SYSTEM/'lib/egl').glob('*.so'))
starts+=list((SYSTEM/'vendor/lib/egl').glob('*.so'))
search=[SYSTEM/'lib',SYSTEM/'vendor/lib',SYSTEM/'lib/egl',SYSTEM/'vendor/lib/egl']
pending=list(starts)
results={}
while pending:
    p=pending.pop()
    relative=p.relative_to(SYSTEM).as_posix()
    if relative in results:
        continue
    needed=dynamic(p)
    resolved={}
    for name in needed:
        options=[base/name for base in search if (base/name).is_file()]
        resolved[name]=[v.relative_to(SYSTEM).as_posix() for v in options]
        if options:
            pending.append(options[0])
    results[relative]={'needed':needed,'candidates_in_dump':resolved}
report={'scope':'Static DT_NEEDED graph only; candidate order does not validate Android 8.1 linker namespaces or symbol ABI.',
        'entrypoints':[p.relative_to(SYSTEM).as_posix() for p in starts],
        'libraries':results,
        'unresolved':sorted({n for r in results.values() for n,paths in r['candidates_in_dump'].items() if not paths})}
OUT.write_text(json.dumps(report,indent=2),encoding='utf-8')
print('ELF32 libraries inspected:',len(results))
print('Unresolved DT_NEEDED names:',report['unresolved'])
