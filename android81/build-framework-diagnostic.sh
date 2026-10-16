#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/configure-jack-framework.py"
python3 "$TASK_DIR/patch-art-host-mmap.py"
bash "$TASK_DIR/stage-device.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
# Host dex2oat also resolves a dalvik-cache directory while loading boot.art.
export ANDROID_DATA=/root/android81-leo/host-android-data
mkdir -p "$ANDROID_DATA/dalvik-cache"
make -j8 systemimage leo_framework_mount leo_framework_exec leo_diag_bootstrap
