"""Extract public trust certificates from the original kernel, offline only."""
from pathlib import Path
import hashlib
import json
import re
import zlib
from cryptography import x509
from cryptography.hazmat.primitives import serialization

root = Path(__file__).resolve().parent.parent
out = root / 'android81/output/kernel-uhid'
out.mkdir(parents=True, exist_ok=True)
image = (root / 'analysis/boot/kernel').read_bytes()
payload = None
for match in re.finditer(b'\x1f\x8b\x08', image):
    try:
        candidate = zlib.decompress(image[match.start():], 31)
        if b'Linux version ' in candidate:
            payload = candidate
            break
    except zlib.error:
        pass
assert payload is not None, 'No Linux payload found'
certs = {}
for match in re.finditer(b'\x30\x82', payload):
    start = match.start()
    size = int.from_bytes(payload[start+2:start+4], 'big') + 4
    if not 256 <= size <= 16384:
        continue
    der = payload[start:start+size]
    try:
        cert = x509.load_der_x509_certificate(der)
    except ValueError:
        continue
    certs[hashlib.sha256(der).hexdigest()] = cert
assert certs, 'No original public trust certificate recovered; stop'
(out / 'stock-trusted.pem').write_bytes(b''.join(
    cert.public_bytes(serialization.Encoding.PEM) for cert in certs.values()))
report = {
    'original_kernel_sha256': hashlib.sha256(image).hexdigest(),
    'certificates': [{'sha256': digest, 'subject': cert.subject.rfc4514_string()}
                     for digest, cert in certs.items()],
    'private_signing_key_available': False,
    'note': 'Public certificates only; trust membership must be checked in built kernel.',
}
(out / 'certificate-report.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
