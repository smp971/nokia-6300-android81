#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/patch-phone-data-usage.py"
python3 "$TASK_DIR/patch-phone-lte.py"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 TeleService
cp out/target/product/leo/system/priv-app/TeleService/TeleService.apk "$TASK_DIR/output/phone-settings.apk"
