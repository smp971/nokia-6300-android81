"""Patch the Nokia 6300 4G boot image as in the BananaHackers/nokia-leo root guide.

Changes (all from the guide):
  default.prop            ro.secure=0, security.perf_harden=0, ro.debuggable=1
  init.qcom.early_boot.sh append `setenforce 0`
  init.rc                 comment out `setprop selinux.reload_policy 1`
  kernel cmdline          append `androidboot.selinux=permissive enforcing=0`
"""
import gzip
import hashlib
import struct
import sys

SRC = sys.argv[1]
DST = sys.argv[2]

d = open(SRC, "rb").read()
assert d[:8] == b"ANDROID!"
ksz, kaddr, rsz, raddr, ssz, saddr, tags, page, hver, osv = struct.unpack("<10I", d[8:48])
assert ssz == 0 and hver == 0 and page == 2048
cmdline = d[64:576].split(b"\0")[0]
extra = d[608:1024].split(b"\0")[0]


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
        ino, mode, uid, gid, nlink, mtime, fsz, dmaj, dmin, rmaj, rmin, nsz, chk = f
        name = data[i + 110:i + 110 + nsz - 1].decode()
        off = (i + 110 + nsz + 3) & ~3
        body = data[off:off + fsz]
        ents.append([f, name, bytearray(body)])
        i = (off + fsz + 3) & ~3
        if name == "TRAILER!!!":
            break
    return ents


def build(ents):
    out = bytearray()
    for f, name, body in ents:
        f = list(f)
        f[6] = len(body)
        nb = name.encode() + b"\0"
        f[11] = len(nb)
        out += b"070701" + b"".join(b"%08X" % v for v in f)
        out += nb
        out += b"\0" * (-len(out) % 4)
        out += body
        out += b"\0" * (-len(out) % 4)
    out += b"\0" * (-len(out) % 512)
    return bytes(out)


ents = parse(cpio)
byname = {e[1]: e for e in ents}
log = []

# default.prop
dp = byname["default.prop"][2].decode()
for old, new in (("ro.secure=1", "ro.secure=0"),
                 ("security.perf_harden=1", "security.perf_harden=0"),
                 ("ro.debuggable=0", "ro.debuggable=1")):
    assert dp.count(old) == 1, old
    dp = dp.replace(old, new)
    log.append("default.prop: %s -> %s" % (old, new))
byname["default.prop"][2] = bytearray(dp.encode())

# init.qcom.early_boot.sh
eb = byname["init.qcom.early_boot.sh"][2]
if not eb.endswith(b"\n"):
    eb += b"\n"
eb += b"\nsetenforce 0\n"
byname["init.qcom.early_boot.sh"][2] = eb
log.append("init.qcom.early_boot.sh: appended setenforce 0")

# init.rc
rc = byname["init.rc"][2].decode()
target = "    setprop selinux.reload_policy 1"
assert rc.count(target) == 1
rc = rc.replace(target, "    #setprop selinux.reload_policy 1")
byname["init.rc"][2] = bytearray(rc.encode())
log.append("init.rc: commented selinux.reload_policy")

# cmdline
add = b" androidboot.selinux=permissive enforcing=0"
assert b"selinux" not in cmdline
new_cmd = cmdline + add
assert len(new_cmd) < 512
log.append("cmdline: +%s" % add.decode().strip())

new_cpio = build(ents)
new_rd = gzip.compress(new_cpio, 9, mtime=0)

hdr = bytearray(d[:page])
hdr[64:576] = new_cmd.ljust(512, b"\0")
struct.pack_into("<I", hdr, 16, len(new_rd))  # ramdisk size
sha = hashlib.sha1()
sha.update(kern)
sha.update(struct.pack("<I", ksz))
sha.update(new_rd)
sha.update(struct.pack("<I", len(new_rd)))
sha.update(b"")
sha.update(struct.pack("<I", 0))
hdr[576:596] = sha.digest()
hdr[596:608] = b"\0" * 12

img = bytes(hdr) + kern + b"\0" * (up(ksz) - ksz) + new_rd + b"\0" * (up(len(new_rd)) - len(new_rd))
open(DST, "wb").write(img)
for l in log:
    print(l)
print("ramdisk %d -> %d bytes; image %d bytes" % (rsz, len(new_rd), len(img)))
