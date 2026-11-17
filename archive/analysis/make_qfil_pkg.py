"""Build a QFIL-ready package ("safe" set) from the nokia-leo TA-1286 v20.00.17.01 dump.

Includes only: system, boot, recovery, cache, splash, misc.
Excludes: GPT (no re-partitioning), bootchain (sbl1/aboot/rpm/tz/cmnlib/keymaster/devcfg/apdp + bak),
          and all device-unique partitions (modem*, fsg, persist, *_nv, devinfo, config, oem, ...).
Uses rawprogram0.xml (matches the phone's real GPT), NOT qfil_rawprogram0.xml.
"""
import hashlib
import os
import re
import shutil

SRC = r"D:\ROMS\nokia-6300-ta1286-dump"
OUT = r"D:\ROMS\nokia-6300-qfil-safe"
KEEP = ["boot", "recovery", "system", "cache", "splash", "misc"]

os.makedirs(OUT, exist_ok=True)
xml = open(os.path.join(SRC, "meta", "rawprogram0.xml"), encoding="utf-8").read()

progs = {}
for m in re.finditer(r"<program [^>]*/>", xml):
    t = m.group(0)
    label = re.search(r'label="([^"]*)"', t).group(1)
    progs[label] = t

lines = ['<?xml version="1.0" ?>', "<data>", "  <!-- Nokia 6300 4G (TA-1286) v20.00.17.01 - SAFE set: " + ", ".join(KEEP) + " -->"]
rows = []
for label in KEEP:
    t = progs[label]
    fn = re.search(r'filename="([^"]*)"', t).group(1)
    n = int(re.search(r'num_partition_sectors="(\d+)"', t).group(1))
    start = int(re.search(r'start_sector="(\d+)"', t).group(1))
    src = os.path.join(SRC, "partitions", fn)
    size = os.path.getsize(src)
    assert size == n * 512, (label, size, n * 512)
    dst = os.path.join(OUT, fn)
    if os.path.exists(dst):
        os.remove(dst)
    try:
        os.link(src, dst)  # hard link, same volume: no extra disk space
        how = "hardlink"
    except OSError:
        shutil.copy2(src, dst)
        how = "copy"
    sha = hashlib.sha256(open(dst, "rb").read()).hexdigest()
    lines.append("  " + t)
    rows.append((label, fn, start, n, size, how, sha))
lines.append("</data>")
open(os.path.join(OUT, "rawprogram0.xml"), "w", encoding="utf-8").write("\n".join(lines) + "\n")

# No GPT writing, so no GPT patches are needed: empty patch file.
open(os.path.join(OUT, "patch0.xml"), "w", encoding="utf-8").write(
    '<?xml version="1.0" ?>\n<patches>\n  <!-- intentionally empty: partition table is not rewritten -->\n</patches>\n')

shutil.copy2(os.path.join(SRC, "..", "edl-tools", "edl-fast", "8k.mbn"), os.path.join(OUT, "8k.mbn"))

with open(os.path.join(OUT, "SHA256SUMS.txt"), "w") as f:
    for label, fn, start, n, size, how, sha in rows:
        f.write("%s  %s\n" % (sha, fn))
    f.write("%s  %s\n" % (hashlib.sha256(open(os.path.join(OUT, "8k.mbn"), "rb").read()).hexdigest(), "8k.mbn"))

print("%-9s %-12s %9s %9s %12s  %s" % ("label", "file", "start", "sectors", "bytes", "how"))
for label, fn, start, n, size, how, sha in rows:
    print("%-9s %-12s %9d %9d %12d  %s" % (label, fn, start, n, size, how))
