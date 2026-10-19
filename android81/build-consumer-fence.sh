#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/patch-leo-consumer-fence.py"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 libgui
cp out/target/product/leo/system/lib/libgui.so "$TASK_DIR/output/libgui-leo-fence.so"
sha256sum "$TASK_DIR/output/libgui-leo-fence.so"
