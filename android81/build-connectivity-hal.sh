#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
cmp -s "$TASK_DIR/device/nokia/leo/BoardConfig.mk" /root/android81-leo/src/device/nokia/leo/BoardConfig.mk || cp "$TASK_DIR/device/nokia/leo/BoardConfig.mk" /root/android81-leo/src/device/nokia/leo/
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 android.hardware.bluetooth@1.0-service android.hardware.bluetooth@1.0-impl \
    android.hardware.wifi@1.0-service wpa_supplicant wpa_cli hostapd
