#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/patch-settings-optional-wifi.py"
python3 "$TASK_DIR/patch-settings-rendering.py"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
mkdir -p "$ANDROID_DATA/dalvik-cache"
make -j4 Settings
cp out/target/product/leo/system/priv-app/Settings/Settings.apk "$TASK_DIR/output/settings-canvas.apk"
