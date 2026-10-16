#!/bin/bash
# Run only after a successful framework systemimage build.
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC=/root/android81-leo/src
PRODUCT="$SRC/out/target/product/leo"
DEST="$TASK_DIR/output"
test -s "$PRODUCT/system/framework/services.jar"
test -s "$PRODUCT/system/framework/framework.jar"
test -s "$PRODUCT/system/framework/arm/boot.art"
test -s "$PRODUCT/system/priv-app/Launcher3Go/Launcher3Go.apk"
test -s "$PRODUCT/system/vendor/lib/hw/hwcomposer.leo.so"
# Construct a new image from the installed tree, without altering build outputs.
STAGE=/root/android81-leo/framework-package
mkdir -p "$STAGE/system"
rsync -a "$PRODUCT/system/" "$STAGE/system/"
cp "$PRODUCT/system/vendor/lib/hw/hwcomposer.leo.so" "$STAGE/system/vendor/lib/hw/hwcomposer.default.so"
cp "$TASK_DIR/device/nokia/leo/framework.fstab" "$STAGE/system/etc/recovery.fstab"
if [ "${LEO_INTERNAL_PACKAGE:-0}" = 1 ]; then
    cp "$TASK_DIR/device/nokia/leo/internal.fstab" "$STAGE/system/etc/recovery.fstab"
    for layout in matrix_keypad gpio-keys qpnp_pon; do
        cp "$TASK_DIR/device/nokia/leo/$layout.kl" "$STAGE/system/usr/keylayout/$layout.kl"
    done
    test -s "$STAGE/system/vendor/lib/hw/android.hardware.keymaster@3.0-impl.so"
    mkdir -p "$STAGE/system/app/LeoShell"
    cp "$DEST/leo-shell.apk" "$STAGE/system/app/LeoShell/LeoShell.apk"
    cp "$TASK_DIR/leo-first-setup.sh" "$STAGE/system/etc/leo-first-setup.sh"
    chmod 644 "$STAGE/system/etc/leo-first-setup.sh"
fi
rm -f "$STAGE/system/bin/install-recovery.sh"
CONTEXTS="$PRODUCT/obj/ETC/file_contexts.bin_intermediates/file_contexts.bin"
test -s "$CONTEXTS"
"$SRC/out/host/linux-x86/bin/make_ext4fs" -S "$CONTEXTS" -l 838860800 -a system "$STAGE/system.raw.img" "$STAGE/system" "$PRODUCT"
test "$(stat -c %s "$STAGE/system.raw.img")" -eq 838860800
# A separate empty ext4 DATA image; never replace a previously generated copy.
if [ ! -e "$STAGE/data.raw.img" ]; then
    truncate -s 536870912 "$STAGE/data.raw.img"
    mke2fs -q -t ext4 -O '^64bit,^metadata_csum' -L leo_test_data -F "$STAGE/data.raw.img"
fi
gzip -1 -c "$STAGE/system.raw.img" > "$STAGE/system.raw.img.gz"
gzip -1 -c "$STAGE/data.raw.img" > "$STAGE/data.raw.img.gz"
tar -C "$PRODUCT/root" -cf "$DEST/framework-root.tar" .
cp "$PRODUCT/system/bin/leo_framework_mount" "$DEST/leo-framework-mount"
cp "$PRODUCT/system/bin/leo_framework_exec" "$DEST/leo-framework-exec"
cp "$PRODUCT/system/bin/leo_diag_bootstrap" "$DEST/leo-diag-bootstrap-framework"
python3 - "$STAGE" "$DEST" <<'PY'
import hashlib,json,sys
from pathlib import Path
stage,dest=map(Path,sys.argv[1:])
def checksum(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()
report={'images':{name:{'path':str(stage/name),'bytes':(stage/name).stat().st_size,'sha256':checksum(stage/name)} for name in ('system.raw.img','data.raw.img')},'internal_system_replacement':False,'data_image_initial_only':True}
for name,info in report['images'].items():
    compressed=stage/(name+'.gz')
    info['compressed']={'path':str(compressed),'bytes':compressed.stat().st_size,'sha256':checksum(compressed)}
(dest/'framework-runtime.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
PY
