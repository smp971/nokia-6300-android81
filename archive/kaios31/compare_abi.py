"""Static ABI comparison. Never loads or executes donor binaries."""
from pathlib import Path
import json
import re
import subprocess

HERE=Path(__file__).resolve().parent
STOCK=HERE.parent/'analysis/system'
DONOR=Path('/root/kaios31-leo/extracted/system')
cache={}
def symbols(path):
    key=str(path)
    if key in cache:
        return cache[key]
    p=subprocess.run(['readelf','-W','--dyn-syms',key],capture_output=True,text=True)
    exports=set()
    undefined=set()
    for line in p.stdout.splitlines():
        s=line.split()
        if len(s)<8 or not s[0].rstrip(':').isdigit():
            continue
        name=s[7].split('@')[0]
        if s[6]!='UND':
            exports.add(name)
        elif s[4]=='GLOBAL':
            undefined.add(name)
    cache[key]=(exports,undefined)
    return exports,undefined

def needed(path):
    p=subprocess.run(['readelf','-d',str(path)],capture_output=True,text=True)
    return re.findall(r'\(NEEDED\).*?\[(.*?)\]',p.stdout)

def resolve(root,name):
    for folder in ('b2g','lib','vendor/lib','lib/egl','vendor/lib/egl'):
        p=root/folder/name
        if p.is_file():
            return p
    # Extracted Android 10 absolute /apex symlinks do not resolve on the host.
    # Resolve their actual offline payloads, never against host /apex.
    for pattern in ('*/lib/bionic/'+name,'*/lib/'+name):
        for p in (root/'apex').glob(pattern):
            if p.is_file() and not p.is_symlink():
                return p
    if root==DONOR:
        for folder in ('lib','lib/egl','lib/hw'):
            p=Path('/root/kaios31-leo/vendor-extracted')/folder/name
            if p.is_file() and not p.is_symlink():
                return p
    return None

def closure(root,start):
    queue=[start]
    found={}
    missing=set()
    while queue:
        p=queue.pop()
        if str(p) in found:
            continue
        found[str(p)]=p
        for name in needed(p):
            v=resolve(root,name)
            if v:
                queue.append(v)
            else:
                missing.add(name)
    return list(found.values()), sorted(missing)

report={'scope':'Static, name-based symbol comparison only. Ignores symbol versions, structures, behavior and runtime linker namespaces.',
        'libxul_dependency_names':{},'platform_libraries':{},'binder_ioctl_literal_scan':{}}
xul=DONOR/'b2g/libxul.so'
if xul.exists():
    deps=needed(xul)
    report['libxul_dependency_names']={name:{'donor':str(resolve(DONOR,name)),
                                           'stock':str(resolve(STOCK,name))} for name in deps}
    report['libxul_names_missing_in_stock']=[n for n in deps if resolve(STOCK,n) is None]
    files,missing=closure(DONOR,xul)
    exports=set()
    for p in files:
        exports.update(symbols(p)[0])
    report['donor_closure']={'library_count':len(files),'missing_dependencies':missing,
                            'libxul_global_undefined_unprovided':sorted(symbols(xul)[1]-exports)}
for name in ('libc.so','libm.so','libdl.so','libbinder.so','libgui.so','libui.so','libutils.so','libcutils.so'):
    old=resolve(STOCK,name)
    new=resolve(DONOR,name)
    if old and new:
        added=symbols(new)[0]-symbols(old)[0]
        required=symbols(xul)[1] & added if xul.exists() else set()
        report['platform_libraries'][name]={'donor_exports_absent_in_stock_count':len(added),
                                         'donor_exports_absent_in_stock_sample':sorted(added)[:30],
                                         'libxul_requires_from_this_difference':sorted(required)}
for label,root in (('stock',STOCK),('donor',DONOR)):
    path=resolve(root,'libbinder.so')
    if path:
        data=path.read_bytes()
        report['binder_ioctl_literal_scan'][label]={
            'binder_write_read_32_literal_count':data.count(bytes.fromhex('016218c0')),
            'binder_write_read_64_literal_count':data.count(bytes.fromhex('016230c0')),
            'note':'Literal presence is a diagnostic clue, not a validated execution path.'}
(HERE/'abi-comparison.json').write_text(json.dumps(report,indent=2))
print(json.dumps({'direct_dependencies_absent_in_stock':len(report.get('libxul_names_missing_in_stock',[])),
                  'closure_libraries':report.get('donor_closure',{}).get('library_count'),
                  'missing_donor_dependencies':report.get('donor_closure',{}).get('missing_dependencies'),
                  'libc_required_new_symbols':report['platform_libraries'].get('libc.so',{}).get('libxul_requires_from_this_difference'),
                  'binder':report['binder_ioctl_literal_scan']},indent=2))
