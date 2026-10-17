#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
bash "$TASK_DIR/build-adreno-buffer-probe.sh"
python3 "$TASK_DIR/patch-adreno-hwui-fence.py"
bash "$TASK_DIR/stage-device.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 hwcomposer.leo libhwui
cp out/target/product/leo/system/lib/libhwui.so "$TASK_DIR/output/libhwui-adreno.so"
cp out/target/product/leo/system/vendor/lib/hw/hwcomposer.leo.so "$TASK_DIR/output/hwcomposer-adreno.so"
