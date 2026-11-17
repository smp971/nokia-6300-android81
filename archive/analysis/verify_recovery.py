import gzip
import struct
import sys

NUL = bytes([0])


def load(p):
    d = open(p, "rb").read()
    ksz, _, rsz, _, _, _, _, page, _, _ = struct.unpack("<10I", d[8:48])
    up = lambda n: (n + page - 1) // page * page
    k = d[page:page + ksz]
    cpio = gzip.decompress(d[page + up(ksz):page + up(ksz) + rsz])
    ents, i = {}, 0
    while True:
        h = cpio[i:i + 110]
        f = [int(h[6 + 8 * j:14 + 8 * j], 16) for j in range(13)]
        nsz, fsz = f[11], f[6]
        name = cpio[i + 110:i + 110 + nsz - 1].decode()
        off = (i + 110 + nsz + 3) & ~3
        if name == "TRAILER!!!":
            break
        ents[name] = (f[1], cpio[off:off + fsz])
        i = (off + fsz + 3) & ~3
    return k, ents, d[64:576].split(NUL)[0].decode(), len(d)


a = load(sys.argv[1])
b = load(sys.argv[2])
print("kernel identical:", a[0] == b[0])
print("same entries:", set(a[1]) == set(b[1]), "| modes kept:", all(a[1][n][0] == b[1][n][0] for n in a[1]))
print("changed files:", [n for n in a[1] if a[1][n] != b[1][n]])
print([l for l in b[1]["default.prop"][1].decode().splitlines() if l.startswith(("ro.secure", "ro.debuggable", "ro.adb.secure"))])
print("cmdline tail:", b[2][-48:])
print("image size:", b[3], "multiple of 512:", b[3] % 512 == 0)
