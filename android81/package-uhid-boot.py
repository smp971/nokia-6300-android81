"""Replace only the kernel, preserving live boot ramdisk/header/appended DTBs."""
from pathlib import Path
import hashlib
import json
import struct

out = Path(__file__).resolve().parent / 'output/kernel-uhid'
old = (out / 'preflash-boot.img').read_bytes()
new = (out / 'zImage').read_bytes()
report = json.loads((out / 'module-compatibility.json').read_text())
for key in ['all_signatures_valid', 'all_symbol_crcs_match', 'original_certificates_embedded',
            'release_matches_original', 'uhid_built_in', 'signature_enforcement_retained',
            'only_expected_config_changes']:
    assert report[key], key
assert hashlib.sha256(new).hexdigest() == (out / 'SHA256SUMS').read_text().split()[0]
assert len(old) == 33554432 and old[:8] == b'ANDROID!'
k, ka, r, ra, s, sa, tags, page, dt, unused = struct.unpack_from('<10I', old, 8)
assert page == 2048 and s == dt == unused == 0
align = lambda size: (size + page - 1) // page * page
original_kernel = old[page:page+k]
old_zsize = struct.unpack_from('<I', original_kernel, 0x2c)[0] - struct.unpack_from('<I', original_kernel, 0x28)[0]
assert struct.unpack_from('<I', original_kernel, 0x24)[0] == 0x016f2818
assert struct.unpack_from('<I', new, 0x24)[0] == 0x016f2818
assert struct.unpack_from('<I', new, 0x2c)[0] - struct.unpack_from('<I', new, 0x28)[0] == len(new)
dtbs = original_kernel[old_zsize:]
offset = 0
dtb_hashes = []
while offset < len(dtbs):
    assert dtbs[offset:offset+4] == b'\xd0\x0d\xfe\xed'
    size = struct.unpack_from('>I', dtbs, offset+4)[0]
    assert 40 <= size <= len(dtbs)-offset
    dtb_hashes.append(hashlib.sha256(dtbs[offset:offset+size]).hexdigest())
    offset += size
assert dtb_hashes
ramdisk = old[page+align(k):page+align(k)+r]
assert not any(old[page+align(k)+align(r):]), 'Unexpected boot trailer: stop'
kernel = new + dtbs
assert page + align(len(kernel)) + align(r) <= len(old)
image = bytearray(old[:page] + bytes(len(old)-page))
image[page:page+len(kernel)] = kernel
rdstart = page + align(len(kernel))
image[rdstart:rdstart+r] = ramdisk
struct.pack_into('<I', image, 8, len(kernel))
digest = hashlib.sha1()
for data in (kernel, ramdisk, b''):
    digest.update(data)
    digest.update(struct.pack('<I', len(data)))
image[576:596] = digest.digest()
assert image[12:576] == old[12:576] and image[596:page] == old[596:page]
assert image[rdstart:rdstart+r] == ramdisk
assert image[page+len(new):page+len(kernel)] == dtbs
path = out / 'boot-uhid.img'
path.write_bytes(image)
metadata = {'previous_boot_sha256': hashlib.sha256(old).hexdigest(),
            'boot_sha256': hashlib.sha256(image).hexdigest(),
            'zimage_sha256': hashlib.sha256(new).hexdigest(),
            'bytes': len(image), 'ramdisk_preserved': True, 'header_addresses_preserved': True,
            'cmdline_preserved': True, 'original_appended_dtbs': len(dtb_hashes),
            'dtb_sha256': dtb_hashes, 'ramdisk_sha256': hashlib.sha256(ramdisk).hexdigest()}
(out / 'boot-uhid.json').write_text(json.dumps(metadata, indent=2))
print(json.dumps({key: value for key, value in metadata.items() if key != 'dtb_sha256'}, indent=2))
