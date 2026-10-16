import gzip
import struct
import sys

d = open(sys.argv[1], "rb").read()
ksz, kaddr, rsz, raddr, ssz, saddr, tags, page, hver, osv = struct.unpack("<10I", d[8:48])
print("kernel", ksz, "ramdisk", rsz, "second", ssz, "page", page, "dt/hver", hver)
print("cmdline:", d[64:576].split(b"\0")[0].decode())
up = lambda n: (n + page - 1) // page * page
kern = d[page:page + ksz]
rd = d[page + up(ksz):page + up(ksz) + rsz]
print("ramdisk magic", rd[:4].hex())
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
print("entries:", len(ents))
print([n for n in ents if "/" not in n][:60])
dp = ents["default.prop"][1].decode()
print("--- default.prop:")
print("\n".join(l for l in dp.splitlines() if l.startswith(("ro.secure", "ro.debuggable", "ro.adb", "security", "persist"))))
for n in ("init.rc", "init.recovery.qcom.rc", "ueventd.rc", "ueventd.qcom.rc"):
    if n in ents:
        t = ents[n][1].decode(errors="replace")
        print("---", n, len(t))
        for l in t.splitlines():
            if "adbd" in l or "selinux" in l.lower() or "by-name" in l or "/dev/block" in l:
                print("   ", l.strip()[:120])
print("sbin:", [n for n in ents if n.startswith("sbin/")])
print("system/bin:", [n for n in ents if n.startswith("system/bin")][:10])
