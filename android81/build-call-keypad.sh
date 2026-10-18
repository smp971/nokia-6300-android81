#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/patch-call-keypad.py"
cp "$TASK_DIR/device/nokia/leo/matrix_keypad.kl" /root/android81-leo/src/device/nokia/leo/
cp "$TASK_DIR/device/nokia/leo/device-common.mk" /root/android81-leo/src/device/nokia/leo/
mkdir -p /root/android81-leo/src/device/nokia/leo/overlay
cp -r "$TASK_DIR/device/nokia/leo/overlay/." /root/android81-leo/src/device/nokia/leo/overlay/
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 out/target/product/leo/obj/APPS/Dialer_intermediates/package.apk
cp out/target/product/leo/obj/APPS/Dialer_intermediates/package.apk "$TASK_DIR/output/leo-keypad-dialer.apk"
