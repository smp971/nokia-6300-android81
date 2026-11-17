"""Read-only static inventory of extracted KaiOS webapps; never modifies firmware."""
import configparser
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'analysis/system/b2g'
OUTPUT = ROOT / 'android81/output/kaios-ui-inventory.json'
REPORT = ROOT / 'android81/KAIOS-UI-INVENTORY.md'
registry = json.loads((SOURCE / 'webapps/webapps.json').read_text(encoding='utf-8-sig'))
config = configparser.ConfigParser(interpolation=None)
config.read(SOURCE / 'application.ini', encoding='utf-8')
items = []
for app_id in sorted(registry):
    folder = SOURCE / 'webapps' / app_id
    archive = folder / 'application.zip'
    manifest_path = folder / 'manifest.webapp'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig')) if manifest_path.exists() else {}
    apis, counts, files, compressed, unpacked = set(), {}, 0, 0, 0
    digest = None
    if archive.exists():
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        compressed = archive.stat().st_size
        with zipfile.ZipFile(archive) as z:
            if not manifest and 'manifest.webapp' in z.namelist():
                manifest = json.loads(z.read('manifest.webapp').decode('utf-8-sig'))
            for entry in z.infolist():
                if entry.is_dir():
                    continue
                files += 1
                unpacked += entry.file_size
                suffix = Path(entry.filename).suffix.lower()
                counts[suffix] = counts.get(suffix, 0) + 1
                if suffix in ('.js', '.html', '.xhtml', '.css'):
                    text = z.read(entry).decode('utf-8', errors='replace')
                    apis.update(re.findall(r'\bnavigator\s*\.\s*(moz\w+|getDeviceStorages?|engmodeExtension|battery|b2g)\b', text))
                    apis.update(re.findall(r'\b(MozActivity|MozSettingsEvent|SettingsListener|SettingsHelper)\b', text))
    items.append(dict(id=app_id, name=manifest.get('name', app_id),
                      type=manifest.get('type', 'web'), role=manifest.get('role', ''),
                      launch_path=manifest.get('launch_path', ''),
                      permissions=sorted(manifest.get('permissions', {})),
                      connections=sorted(manifest.get('connections', {})),
                      entry_points=sorted(manifest.get('entry_points', {})),
                      apis=sorted(apis), asset_counts=counts, files=files,
                      archive_bytes=compressed, unpacked_bytes=unpacked, sha256=digest))
services = []
for rc in sorted((ROOT / 'analysis/boot/rd').rglob('*.rc')):
    for name, executable in re.findall(r'^service\s+(\S+)\s+(\S+)', rc.read_text(errors='replace'), re.M):
        services.append(dict(name=name, executable=executable,
                             source=rc.relative_to(ROOT / 'analysis/boot/rd').as_posix()))
# Mozilla's optimized omni.ja puts the central directory after a preload word.
# Read only consecutive central headers; never execute or extract their payloads.
omni = (SOURCE / 'omni.ja').read_bytes()
module_names = []
offset = 4
while omni[offset:offset+4] == b'PK\x01\x02':
    name_len, extra_len, comment_len = struct.unpack_from('<HHH', omni, offset+28)
    name = omni[offset+46:offset+46+name_len].decode('utf-8')
    module_names.append(name)
    offset += 46 + name_len + extra_len + comment_len
assert module_names, 'Expected Mozilla optimized central directory'
result = dict(source='analysis/system/b2g', version=config.get('App', 'Version'),
              gecko=config.get('Gecko', 'MinVersion'), apps=items,
              init_declarations=services, omni_members=module_names)
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def cell(text):
    return str(text).replace('|', '\\|').replace('\n', ' ')

lines = [
    '# Inventarul interfeței KaiOS din dump', '',
    'Data analizei: **19 octombrie 2026**. Scanare statică, fără instalare sau modificări pe telefon.', '',
    f"Baza extrasă declară **KaiOS {result['version']}**, Gecko **{result['gecko']}** în `application.ini`.",
    f"Registrul conține **{len(items)} de aplicații**, dintre care **{sum(bool(i['sha256']) for i in items)}** au `application.zip` local.",
    f"Pachetele ocupă aproximativ **{sum(i['archive_bytes'] for i in items)/1048576:.1f} MiB** comprimat; aceasta nu este memoria RAM necesară rulării.", '',
    '## Inventar complet al registrului', '',
    'API = numărul denumirilor de API specifice detectate în JS/HTML/CSS. Permisiunile provin din manifest.',
    'Zero API detectate nu înseamnă compatibilitate Android: codul poate utiliza aliasuri, worker-e, IPC sau biblioteci dinamice.', '',
    '| Aplicație | ID | Tip / rol | ZIP MiB | Permisiuni | API |',
    '| --- | --- | --- | ---: | ---: | ---: |',
]
for i in items:
    lines.append('| ' + ' | '.join(map(cell, [i['name'], i['id'], i['type'] + (' / ' + i['role'] if i['role'] else ''),
                                               f"{i['archive_bytes']/1048576:.2f}" if i['sha256'] else 'lipsește',
                                               len(i['permissions']), len(i['apis'])])) + ' |')
lines += ['', '## Fundația interfeței și dependențe observate', '']
for base in ['system', 'launcher', 'default_theme', 'keyboard', 'settings', 'callscreen', 'communications', 'contact', 'sms']:
    for i in items:
        if i['id'] == base + '.gaiamobile.org':
            lines += [f"### {i['id']}", '',
                      '**API detectate:** ' + (', '.join('`' + a + '`' for a in i['apis']) or 'Niciunul din tiparele căutate.') + '.', '',
                      '**Permisiuni declarate:** ' + (', '.join('`' + p + '`' for p in i['permissions']) or 'Niciuna.') + '.', '',
                      f"**Canale inter-aplicații declarate:** {len(i['connections'])}. **Fișiere în pachet:** {i['files']}.", '']
lines += ['## Servicii native declarate în ramdisk', '',
    f"Au fost găsite **{len(services)} declarații service**, cu **{len({s['name'] for s in services})} de nume distincte**, în configurațiile RC extrase.",
    'Acest inventar include variante condiționale și configurații pentru alte proiecte prezente în ramdisk. Nu afirmă că toate sunt importate sau active pe Leo.',
    'Lista indică executabilul, fără argumente sau date de configurare. Nu copiem fișierele init originale peste Android.', '',
    '| Serviciu declarat | Executabil | Fișier RC |', '| --- | --- | --- |']
for service in services:
    lines.append('| ' + ' | '.join(cell(service[k]) for k in ('name', 'executable', 'source')) + ' |')
lines += ['', '## Componente ale motorului', '',
    f"Indexul central optimizat al `omni.ja` conține **{len(module_names)} intrări**. Formatul nu este acceptat direct de cititorul ZIP standard folosit aici pentru aplicații; scriptul citește separat indexul Mozilla.",
    'Exemple de nume de componente observate în acest index:', '']
for name in module_names:
    if any(key in name.lower() for key in ('radiointerface', 'telephonyservice', 'smsservice', 'settingsservice', 'contactservice', 'alarmsservice', 'rilcontent')):
        lines.append('- `' + name + '`')
lines += ['', '## Ce înseamnă rezultatele', '',
    '- Shell-ul este împărțit între `system`, `launcher`, tema vizuală, tastatură și aplicațiile de funcții; nu este un singur launcher independent.',
    '- HTML/CSS/JS și resursele vizuale sunt disponibile local pentru analiză și prototipare. Nu am demonstrat rularea pachetelor nemodificate în Android WebView.',
    '- Aplicațiile certified și API-urile `moz*` necesită servicii/permisiuni KaiOS. Un WebView Android standard nu furnizează automat aceste implementări.',
    '- `b2g`, `libxul.so` și `omni.ja` sunt prezente în arborele extras. Prezența lor nu demonstrează compatibilitatea cu userspace-ul Android 8.1 sau posibilitatea înlocuirii WebView.',
    '- Aplicațiile găsite în dump nu au fost verificate online. Prezența unui client terț nu confirmă accesul curent la serviciul său.',
    '- Nu publicăm pachetele originale, resursele extrase sau codul vendor prin acest inventar. Reutilizarea și distribuirea fiecărui material necesită verificarea licenței/provenienței sale.', '',
    '## Reproducere și limite', '',
    'Rulează `python analysis/inventory_kaios_ui.py` după extragerea locală a sistemului în `analysis/system`.',
    'Scriptul citește registrul, manifestele și arhivele fără să le extragă sau execute. Hash-urile, contoarele și lista completă de API-uri sunt salvate local în `android81/output/kaios-ui-inventory.json`.',
    'Acest raport include numai metadate și rezultate de analiză, fără cod sau resurse din pachete.',
    'Numerele de API reprezintă indicii textuale, nu un graf complet de dependențe și nici o probă de funcționare.', '',
    'Planul de adaptare: [KAIOS-ALTERNATIVE.md](KAIOS-ALTERNATIVE.md).', '']
REPORT.write_text('\n'.join(lines), encoding='utf-8')
print(json.dumps(dict(version=result['version'], gecko=result['gecko'], apps=len(items),
                      packages=sum(bool(i['sha256']) for i in items), report=str(REPORT)), ensure_ascii=True))
