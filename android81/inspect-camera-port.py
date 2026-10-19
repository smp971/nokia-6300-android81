"""Offline dependency audit of the private KaiOS camera HAL, without loading it."""
from pathlib import Path
from collections import deque
import json
import os
from elftools.elf.elffile import ELFFile

root=Path(__file__).resolve().parent
stock=root.parent/'analysis/system'
# Prefer current Android framework libraries; never replace libbinder/libutils
# with Marshmallow's copies just to satisfy a vendor HAL.
android=Path('//wsl.localhost/kaios/root/android81-leo/src/out/target/product/leo/system' if os.name=='nt' else '/root/android81-leo/src/out/target/product/leo/system')
def metadata(p):
    with p.open('rb') as f:
        e=ELFFile(f);dynamic=e.get_section_by_name('.dynamic');symbols=e.get_section_by_name('.dynsym')
        needed=[t.needed for t in dynamic.iter_tags() if t.entry.d_tag=='DT_NEEDED'] if dynamic else []
        imports={s.name for s in symbols.iter_symbols() if s['st_shndx']=='SHN_UNDEF' and s['st_info']['bind']=='STB_GLOBAL'} if symbols else set()
        exports={s.name for s in symbols.iter_symbols() if s['st_shndx']!='SHN_UNDEF'} if symbols else set()
        return needed,imports,exports
queue=deque([stock/'lib/hw/camera.msm8909.so',stock/'bin/mm-qcamera-daemon'])
seen=set();rows=[];exports=set();all_imports=set();missing=set();platform_seen=set()
while queue:
    p=queue.popleft()
    if p.name in seen:continue
    seen.add(p.name)
    needed,imports,provided=metadata(p);exports.update(provided);all_imports.update(imports)
    rows.append(dict(name=p.name,source='android81' if str(p).startswith(str(android)) else 'kaios-private',needed=needed))
    for name in needed:
        found=next((q for q in [android/'lib'/name,android/'vendor/lib'/name,stock/'lib'/name,stock/'vendor/lib'/name] if q.exists()),None)
        if found and str(found).startswith(str(android)):
            if name not in platform_seen:
                platform_seen.add(name);_,_,provided=metadata(found);exports.update(provided)
        elif found:queue.append(found)
        else:missing.add(name)
unresolved=sorted(all_imports-exports)
report=dict(offline_only=True,original_framework_sdk=23,target_sdk=27,libraries=rows,
            missing_libraries=sorted(missing),unresolved_global_symbols=unresolved,
            platform_libraries_compared=sorted(platform_seen),dynamic_plugins_not_included=True,abi_compatibility_proven=False,camera_capture_tested=False)
out=root/'output/camera-port-audit.json';out.write_text(json.dumps(report,indent=2))
print(json.dumps(dict(libraries=len(rows),missing_libraries=sorted(missing),unresolved_global_symbols=len(unresolved),report=str(out)),indent=2))
