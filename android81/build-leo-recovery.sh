#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
SRC=/root/android81-leo/src
DEST="$SRC/device/nokia/leo/recovery-ui"
mkdir -p "$DEST"
for SOURCE_FILE in "$TASK_DIR/leo-recovery/"*.cpp "$TASK_DIR/leo-recovery/"*.h "$TASK_DIR/leo-recovery/Android.mk" "$TASK_DIR/output/leo-recovery/catalog_generated.h"; do
    TARGET_FILE="$DEST/$(basename "$SOURCE_FILE")"
    cmp -s "$SOURCE_FILE" "$TARGET_FILE" || cp "$SOURCE_FILE" "$TARGET_FILE"
done
cd "$SRC"
source build/envsetup.sh
lunch aosp_leo_go-eng
mmm device/nokia/leo/recovery-ui -j4
cp out/target/product/leo/recovery/root/sbin/leo-recovery "$TASK_DIR/output/leo-recovery/recovery"
cp out/target/product/leo/recovery/root/sbin/leo-boot-selector "$TASK_DIR/output/leo-recovery/selector"
cp bootable/recovery/fonts/12x22.png "$TASK_DIR/output/leo-recovery/font.png"
cp bootable/recovery/fonts/OFL.txt "$TASK_DIR/output/leo-recovery/OFL.txt"
