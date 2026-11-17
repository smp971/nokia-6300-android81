"""QFIL package: restore ALL partitions that the TA-1287 QFIL package overwrote, from the TA-1286 dump.

Written by the TA-1287 package (see QFIL log): default_nv, sbl1(+bak), aboot(+bak), rpm(+bak), tz(+bak), devcfg,
misc, splash, modem, DDR, sec, boot, system, persist, cache, recovery, cmnlib(+bak), keymaster(+bak), userdata, GPT.

We restore all of those except userdata (user data) and GPT (layout was identical: same start sectors).
We do NOT touch partitions the TA-1287 package never wrote (rf_nv, cust_nv, modemst1/2, fsg, traceability, devinfo,
config, oem, keystore, fsc, ssd, apdp, pad).
"""
import hashlib
import os
import re
import shutil

SRC = r"D:\ROMS\nokia-6300-ta1286-dump"
OUT = r"D:\ROMS\nokia-6300-qfil-restore-ta1286"
ORDER = ["default_nv", "sbl1", "sbl1bak", "aboot", "abootbak", "rpm", "rpmbak", "tz", "tzbak", "devcfg",
         "misc", "splash", "modem", "DDR", "sec", "boot", "system", "persist", "cache", "recovery",
         "cmnlib", "cmnlibbak", "keymaster", "keymasterbak"]

os.makedirs(OUT, exist_ok=True)
xml = open(os.path.join(SRC, "meta", "rawprogram0.xml"), encoding="utf-8").read()
progs = {re.search(r'label="([^"]*)"', t).group(1): t for t in re.findall(r"<program [^>]*/>", xml)}

lines = ['<?xml version="1.0" ?>', "<data>",
         "  <!-- Nokia 6300 4G TA-1286 v20.00.17.01: restore of every partition overwritten by the TA-1287 package -->"]
rows = []
for label in ORDER:
    t = progs[label]
    fn = re.search(r'filename="([^"]*)"', t).group(1)
    n = int(re.search(r'num_partition_sectors="(\d+)"', t).group(1))
    start = int(re.search(r'start_sector="(\d+)"', t).group(1))
    src = os.path.join(SRC, "partitions", fn)
    if not os.path.exists(src):
        src = os.path.join(SRC, "sensitive", fn)
    size = os.path.getsize(src)
    assert size == n * 512, (label, size, n * 512)
    dst = os.path.join(OUT, fn)
    if os.path.exists(dst):
        os.remove(dst)
    try:
        os.link(src, dst)
        how = "hardlink"
    except OSError:
        shutil.copy2(src, dst)
        how = "copy"
    sha = hashlib.sha256(open(dst, "rb").read()).hexdigest()
    lines.append("  " + t)
    rows.append((label, fn, start, n, size, how, sha))
lines.append("</data>")
open(os.path.join(OUT, "rawprogram0.xml"), "w", encoding="utf-8").write("\n".join(lines) + "\n")

shutil.copy2(r"D:\ROMS\edl-tools\edl-fast\8k.mbn", os.path.join(OUT, "8k.mbn"))
with open(os.path.join(OUT, "SHA256SUMS.txt"), "w") as f:
    for label, fn, start, n, size, how, sha in rows:
        f.write("%s  %s\n" % (sha, fn))

print("%-12s %-16s %9s %9s %11s" % ("label", "file", "start", "sectors", "MB"))
tot = 0
for label, fn, start, n, size, how, sha in rows:
    tot += size
    print("%-12s %-16s %9d %9d %11.2f" % (label, fn, start, n, size / 1048576))
print("total MB:", round(tot / 1048576, 1), "| partitions:", len(rows))
