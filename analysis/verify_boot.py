import gzip
import hashlib
import struct
import sys


def load(path):
    d = open(path, "rb").read()
    ksz, _, rsz, _, ssz, _, _, page, _, _ = struct.unpack("<10I", d[8:48])
    up = lambda n: (n + page - 1) // page * page
    kern = d[page:page + ksz]
    rd = d[page + up(ksz):page + up(ksz) + rsz]
    cmd = d[64:576].split(b"\0")[0].decode()
    cpio = gzip.decompress(rd)
    ents, i = {}, 0
    while True:
        assert cpio[i:i + 6] == b"070701"
        h = cpio[i:i + 110]
        f = [int(h[6 + 8 * k:14 + 8 * k], 16) for k in range(13)]
        nsz, fsz = f[11], f[6]
        name = cpio[i + 110:i + 110 + nsz - 1].decode()
        off = (i + 110 + nsz + 3) & ~3
        if name == "TRAILER!!!":
            break
        ents[name] = (f[1], cpio[off:off + fsz])
        i = (off + fsz + 3) & ~3
    return d, kern, cmd, ents, d[576:596]


o = load(sys.argv[1])
p = load(sys.argv[2])
print("kernel identical:", hashlib.sha256(o[1]).hexdigest() == hashlib.sha256(p[1]).hexdigest(), len(o[1]))
print("entries:", len(o[3]), "->", len(p[3]), "same names:", set(o[3]) == set(p[3]))
diff = [n for n in o[3] if o[3][n] != p[3][n]]
print("changed files:", diff)
print("modes preserved:", all(o[3][n][0] == p[3][n][0] for n in o[3]))
print("cmdline:", p[2])
print("header size fields ok; id sha1:", p[4].hex())
dp = p[3]["default.prop"][1].decode().splitlines()
print([l for l in dp if l.startswith(("ro.secure", "security.perf", "ro.debuggable", "ro.adb.secure"))])
print("early_boot tail:", p[3]["init.qcom.early_boot.sh"][1].decode().strip().splitlines()[-2:])
print("init.rc reload_policy:", [l.strip() for l in p[3]["init.rc"][1].decode().splitlines() if "reload_policy" in l])
