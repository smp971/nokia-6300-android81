"""Offline checks of original module signatures and rebuilt symbol CRCs.

Requires pyelftools and WSL kaios/OpenSSL. This never loads a module or flashes.
"""
from pathlib import Path
import argparse
import hashlib
import io
import json
import struct
import subprocess
import re
import zlib
from cryptography import x509
from cryptography.hazmat.primitives import serialization
from elftools.elf.elffile import ELFFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--signatures-only', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
out = root / 'android81/output/kernel-uhid'
out.mkdir(parents=True, exist_ok=True)

def wslpath(path):
    p = path.resolve().as_posix()
    assert p[1:3] == ':/'
    return '/mnt/' + p[0].lower() + p[2:]

symbols = {}
original_exports = {}
modules = sorted((root / 'analysis/system/lib/modules').rglob('*.ko'))
if not args.signatures_only:
    for line in (out / 'Module.symvers').read_text().splitlines():
        fields = line.split()
        if len(fields) >= 2:
            symbols[fields[1]] = int(fields[0], 16)
    # Qualcomm audio modules depend on each other as well as the base kernel.
    # Preserve those real providers; do not label their exports missing or alter CRCs.
    for module in modules:
        if not module.stat().st_size:
            continue
        elf = ELFFile(io.BytesIO(module.read_bytes()))
        table = elf.get_section_by_name('.symtab')
        names = {symbol.name for symbol in table.iter_symbols()}
        for symbol in table.iter_symbols():
            if symbol.name.startswith('__crc_') and symbol['st_shndx'] == 'SHN_ABS':
                name = symbol.name[len('__crc_'):]
                if '__ksymtab_' + name in names:
                    original_exports[name] = (symbol['st_value'], module.name)
results = []
for module in modules:
    data = module.read_bytes()
    if not data:
        continue  # extraction placeholders are not modules
    item = {'module': module.relative_to(root).as_posix(),
            'sha256': hashlib.sha256(data).hexdigest()}
    marker = b'~Module signature appended~\n'
    signed = data.endswith(marker)
    item['has_signature'] = signed
    if signed:
        header = len(data) - len(marker) - 12
        algo, digest, ident, signer_len, key_len, signature_len = struct.unpack(
            '>BBBBB3xI', data[header:header+12])
        assert ident == 2 and signer_len == 0 and key_len == 0
        start = header - signature_len
        assert start > 0
        content, signature = out / 'module-content.tmp', out / 'module-signature.tmp'
        content.write_bytes(data[:start])
        signature.write_bytes(data[start:header])
        result = subprocess.run(['wsl', '-d', 'kaios', '--', 'openssl', 'cms',
            '-verify', '-binary', '-inform', 'DER', '-in', wslpath(signature),
            '-content', wslpath(content), '-certfile', wslpath(out / 'stock-trusted.pem'),
            '-noverify', '-out', '/dev/null'], capture_output=True)
        item['signature_valid_with_recovered_certificate'] = result.returncode == 0
        if result.returncode:
            item['signature_error'] = result.stderr.decode(errors='replace')[-600:]
        content.unlink()
        signature.unlink()
    elf = ELFFile(io.BytesIO(data))
    info = elf.get_section_by_name('.modinfo').data().split(b'\0')
    item['vermagic'] = next(x.decode().split('=', 1)[1] for x in info if x.startswith(b'vermagic='))
    if not args.signatures_only:
        versions = elf.get_section_by_name('__versions')
        assert versions is not None and elf.elfclass == 32
        version_data = versions.data()
        assert len(version_data) % 64 == 0
        missing, mismatched, providers = [], [], {}
        for offset in range(0, len(version_data), 64):
            crc = struct.unpack_from('<I', version_data, offset)[0]
            name = version_data[offset+4:offset+64].split(b'\0', 1)[0].decode()
            expected = symbols.get(name)
            if expected is None and name in original_exports:
                expected, provider = original_exports[name]
                providers[name] = provider
            if expected is None:
                missing.append(name)
            elif expected != crc:
                mismatched.append(name)
        item.update(missing_symbols=missing, mismatched_crcs=mismatched,
                    original_module_providers=providers)
    results.append(item)
report = {'offline_only': True, 'symbol_checks_performed': not args.signatures_only,
          'modules': results,
          'all_signatures_valid': all(x.get('signature_valid_with_recovered_certificate') for x in results)}
if not args.signatures_only:
    report['all_symbol_crcs_match'] = all(not x['missing_symbols'] and not x['mismatched_crcs'] for x in results)
    image = (out / 'zImage').read_bytes()
    payload = None
    for match in re.finditer(b'\x1f\x8b\x08', image):
        try:
            candidate = zlib.decompress(image[match.start():], 31)
            if b'Linux version ' in candidate:
                payload = candidate
                break
        except zlib.error:
            pass
    assert payload is not None
    certificates = x509.load_pem_x509_certificates((out / 'stock-trusted.pem').read_bytes())
    report['original_certificates_embedded'] = all(
        cert.public_bytes(serialization.Encoding.DER) in payload for cert in certificates)
    version = re.search(rb'Linux version [^\x00\n]+', payload).group().decode()
    report['kernel_version'] = version
    report['release_matches_original'] = version.startswith('Linux version 4.9.240-perf-g4f9e270fd77f ')
    start = payload.index(b'IKCFG_ST') + 8
    config = zlib.decompress(payload[start:], 31).decode()
    report['uhid_built_in'] = 'CONFIG_UHID=y\n' in config
    report['signature_enforcement_retained'] = 'CONFIG_MODULE_SIG_FORCE=y\n' in config
    def config_values(text):
        values = {}
        for line in text.splitlines():
            if line.startswith('CONFIG_'):
                key, value = line.split('=', 1)
                values[key] = value
            elif line.startswith('# CONFIG_') and line.endswith(' is not set'):
                values[line[2:-11]] = 'n'
        return values
    original = config_values((root / 'android81/device/nokia/leo/reference-stock/kernel.config').read_text())
    rebuilt = config_values(config)
    changes = {key: {'original': original.get(key), 'rebuilt': rebuilt.get(key)}
               for key in original.keys() | rebuilt.keys() if original.get(key) != rebuilt.get(key)}
    report['configuration_changes'] = changes
    allowed = {'CONFIG_UHID', 'CONFIG_LOCALVERSION_AUTO', 'CONFIG_SYSTEM_TRUSTED_KEYS'}
    report['only_expected_config_changes'] = not (changes.keys() - allowed)
name = 'signature-check.json' if args.signatures_only else 'module-compatibility.json'
(out / name).write_text(json.dumps(report, indent=2))
print(json.dumps({k: v for k, v in report.items() if k != 'modules'}, indent=2))
print('Modules checked:', len(results))
checks = ['all_signatures_valid', 'all_symbol_crcs_match', 'original_certificates_embedded',
          'release_matches_original', 'uhid_built_in', 'signature_enforcement_retained',
          'only_expected_config_changes']
raise SystemExit(0 if all(report.get(key, True) for key in checks) else 1)
