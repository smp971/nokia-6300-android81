"""One-time migration of reviewed labels; comments, keys and unknown text stay literal.

Files already importing L are skipped. New labels should call L.t explicitly.
Static label arrays retain their original contents and register for live switching.
"""
from pathlib import Path
import json,re,sys

root=Path(__file__).resolve().parent.parent
catalog=json.loads((root/'localization/en.json').read_text(encoding='utf-8'))
token=re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|\'(?:\\.|[^\'\\])*\'|"(?:\\.|[^"\\])*"')
array=re.compile(r'\bstatic\s+(?:final\s+)?String\s*(?P<dims>(?:\[\]\s*)+)\w+\s*=\s*(?P<new>new\s+String(?:\[\])+\s*)?(?P<open>\{)')
sources=list((root/'visual-shell').glob('*.java'))+[root/'keypad-ime'/n for n in ('KeypadIme.java','DictionaryActivity.java')]
total=0
for path in sources:
    source=path.read_text(encoding='utf-8')
    imported='import ro.leo.i18n.L;' in source
    if imported and '--extend' not in sys.argv:continue
    tokens=list(token.finditer(source));masked=list(source)
    for t in tokens:masked[t.start():t.end()]=' '*len(t.group())
    masked=''.join(masked);regions=[];edits=[]
    for existing in re.finditer(r'L\.array\s*\(',masked):
        start=existing.end()-1;depth=1;end=start+1
        while depth:
            if masked[end]=='(':depth+=1
            elif masked[end]==')':depth-=1
            end+=1
        regions.append((start,end))
    for match in array.finditer(masked):
        start=match.start('open');depth=1;end=start+1
        while depth:
            if masked[end]=='{':depth+=1
            elif masked[end]=='}':depth-=1
            end+=1
        if not any(t.start()>start and t.end()<end and t.group().startswith('"') and json.loads(t.group()) in catalog for t in tokens):continue
        regions.append((start,end))
        begin=match.start('new') if match.group('new') else start
        prefix='L.array(' if match.group('new') else 'L.array(new String'+match.group('dims').replace(' ','')
        edits += [(begin,begin,prefix),(end,end,')')]
    for t in tokens:
        if not t.group().startswith('"') or any(a<t.start()<b for a,b in regions):continue
        value=json.loads(t.group())
        if value not in catalog:continue
        # The first argument of a storage/API key accessor is data, not a label.
        previous=source[max(0,t.start()-100):t.start()]
        if re.search(r'L\.t\s*\(\s*$',previous):continue
        if re.search(r'\b(?:getSharedPreferences|getString|getInt|getLong|getBoolean|optString|optInt|optLong|optBoolean|putString|putInt|putLong|putBoolean|put|getStringExtra|getIntExtra|putExtra)\s*\(\s*$',previous):continue
        if re.search(r'\bcase\s*$',previous):continue
        edits.append((t.start(),t.end(),'L.t('+t.group()+')'))
    if edits:
        for a,b,new in sorted(edits,reverse=True):source=source[:a]+new+source[b:]
        if not imported:source=re.sub(r'(package\s+[\w.]+;)',r'\1\nimport ro.leo.i18n.L;',source,count=1)
        source=source.replace('new java.util.Locale("ro")','L.locale()').replace('new Locale("ro")','L.locale()') if path.parent.name=='visual-shell' else source
        path.write_text(source,encoding='utf-8');total+=len(edits)
print('Migrated label references / array boundaries:',total)
