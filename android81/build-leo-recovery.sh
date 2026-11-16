#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
SRC=/root/android81-leo/src
DEST="$SRC/device/nokia/leo/recovery-ui"
OUTPUT=${1:-"$TASK_DIR/output/leo-recovery"}
mkdir -p "$DEST"
for SOURCE_FILE in "$TASK_DIR/leo-recovery/"*.cpp "$TASK_DIR/leo-recovery/"*.h "$TASK_DIR/leo-recovery/Android.mk" "$OUTPUT/catalog_generated.h"; do
    TARGET_FILE="$DEST/$(basename "$SOURCE_FILE")"
    cmp -s "$SOURCE_FILE" "$TARGET_FILE" || cp "$SOURCE_FILE" "$TARGET_FILE"
done
cd "$SRC"
source build/envsetup.sh
lunch aosp_leo_go-eng
mmm device/nokia/leo/recovery-ui -j4
cp out/target/product/leo/recovery/root/sbin/leo-recovery "$OUTPUT/recovery"
cp out/target/product/leo/recovery/root/sbin/leo-boot-selector "$OUTPUT/selector"
cp bootable/recovery/fonts/12x22.png "$OUTPUT/font.png"
cp bootable/recovery/fonts/OFL.txt "$OUTPUT/OFL.txt"
