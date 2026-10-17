#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/patch-hwc1-dispsync.py"
bash "$TASK_DIR/stage-device.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 hwcomposer.leo android.hardware.configstore@1.0-service libsurfaceflinger
cp out/target/product/leo/system/vendor/lib/hw/hwcomposer.leo.so "$TASK_DIR/output/hwcomposer-ui.so"
cp out/target/product/leo/system/vendor/bin/hw/android.hardware.configstore@1.0-service "$TASK_DIR/output/configstore-ui"
cp out/target/product/leo/system/lib/libsurfaceflinger.so "$TASK_DIR/output/libsurfaceflinger-ui.so"
