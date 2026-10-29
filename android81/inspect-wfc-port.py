"""Local, read-only inventory of the stock CNE Wi-Fi/IMS bridge dependency graph."""
from pathlib import Path
import hashlib, importlib.util, json

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('radio_audit',ROOT/'audit-radio.py')
audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
stock=ROOT.parent/'analysis/system'
libraries={p.name:p for folder in ('lib','vendor/lib') for p in (stock/folder).glob('*.so')}
queue=[stock/'bin/cnd'];graph={}
platform={'libc.so','libdl.so','libm.so','liblog.so'}
while queue:
    path=queue.pop()
    if path.name in graph:continue
    dependencies=audit.needed(path)
    graph[path.name]={'stock_path':path.relative_to(stock).as_posix(),
        'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'needed':dependencies}
    queue.extend(libraries[name] for name in dependencies if name not in platform and name in libraries)
framework=stock/'b2g/distribution/bundles/libframework/libframework.so'
report={'cne_graph':graph,'kaios_framework_needed':audit.needed(framework),
    'kaios_feature_property':'persist.cne.feature=4',
    'note':'Dependency presence does not establish Android ABI or event-protocol compatibility.'}
output=ROOT/'output/wfc-port-audit.json';output.write_text(json.dumps(report,indent=2),encoding='utf-8')
print('CNE dependency closure:',len(graph),'binaries/libraries')
print('KaiOS framework dependencies:',', '.join(report['kaios_framework_needed']))
print('Local report:',output)
