"""Patch the Nokia 6300 4G stock RECOVERY image (from the dump) so adbd runs as root.

Changes:
  default.prop  ro.secure=0, ro.debuggable=1
  kernel cmdline  + androidboot.selinux=permissive enforcing=0
Kernel and the rest of the ramdisk stay identical.
"""
import gzip
import hashlib
import struct
import sys

SRC = sys.argv[1]
DST = sys.argv[2]
NUL = bytes([0])

d = open(SRC, "rb").read()
assert d[:8] == b"ANDROID!"
ksz, kaddr, rsz, raddr, ssz, saddr, tags, page, hver, osv = struct.unpack("<10I", d[8:48])
assert ssz == 0 and hver == 0 and page == 2048
cmdline = d[64:576].split(NUL)[0]


def up(n):
    return (n + page - 1) // page * page


kern = d[page:page + ksz]
rd_gz = d[page + up(ksz):page + up(ksz) + rsz]
cpio = gzip.decompress(rd_gz)


def parse(data):
    ents, i = [], 0
    while True:
        assert data[i:i + 6] == b"070701", i
        h = data[i:i + 110]
        f = [int(h[6 + 8 * k:14 + 8 * k], 16) for k in range(13)]
        fsz, nsz = f[6], f[11]
        name = data[i + 110:i + 110 + nsz - 1].decode()
        off = (i + 110 + nsz + 3) & ~3
        ents.append([f, name, bytearray(data[off:off + fsz])])
        i = (off + fsz + 3) & ~3
        if name == "TRAILER!!!":
            break
    return ents


def build(ents):
    out = bytearray()
    for f, name, body in ents:
        f = list(f)
        f[6] = len(body)
        nb = name.encode() + NUL
        f[11] = len(nb)
        out += b"070701" + b"".join(b"%08X" % v for v in f)
        out += nb
        out += NUL * (-len(out) % 4)
        out += body
        out += NUL * (-len(out) % 4)
    out += NUL * (-len(out) % 512)
    return bytes(out)


ents = parse(cpio)
byname = {e[1]: e for e in ents}
log = []

dp = byname["default.prop"][2].decode()
for old, new in (("ro.secure=1", "ro.secure=0"), ("ro.debuggable=0", "ro.debuggable=1")):
    assert dp.count(old) == 1, old
    dp = dp.replace(old, new)
    log.append("default.prop: %s -> %s" % (old, new))
byname["default.prop"][2] = bytearray(dp.encode())

add = b" androidboot.selinux=permissive enforcing=0"
assert b"selinux" not in cmdline
new_cmd = cmdline + add
assert len(new_cmd) < 512
log.append("cmdline: +" + add.decode().strip())

new_rd = gzip.compress(build(ents), 9, mtime=0)

hdr = bytearray(d[:page])
hdr[64:576] = new_cmd.ljust(512, NUL)
struct.pack_into("<I", hdr, 16, len(new_rd))
sha = hashlib.sha1()
sha.update(kern)
sha.update(struct.pack("<I", ksz))
sha.update(new_rd)
sha.update(struct.pack("<I", len(new_rd)))
sha.update(b"")
sha.update(struct.pack("<I", 0))
hdr[576:596] = sha.digest()
hdr[596:608] = NUL * 12

img = bytes(hdr) + kern + NUL * (up(ksz) - ksz) + new_rd + NUL * (up(len(new_rd)) - len(new_rd))
assert len(img) % 512 == 0
open(DST, "wb").write(img)
for line in log:
    print(line)
print("ramdisk %d -> %d bytes; image %d bytes (multiple of 512: %s)" % (rsz, len(new_rd), len(img), len(img) % 512 == 0))
