#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
python3 "$TASK_DIR/prepare-audio-vibrator.py"
source "$TASK_DIR/environment.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 android.hardware.vibrator@1.0-impl vibrator.default
cp out/target/product/leo/system/vendor/lib/hw/android.hardware.vibrator@1.0-impl.so "$TASK_DIR/output/"
