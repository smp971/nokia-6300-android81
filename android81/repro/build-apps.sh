#!/bin/bash
# Compile applications only. No ADB, remount, partition writes or phone reboot.
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC=/root/android81-leo/src
source "$TASK_DIR/environment.sh"
test -f "$SRC/prebuilts/sdk/27/android.jar"
test -f "$TASK_DIR/device/nokia/leo/prebuilt/kernel" || {
    echo 'Missing stock kernel: see REBUILD.md (private device prerequisites).';exit 1;
}
mkdir -p "$TASK_DIR/output" /root/android81-leo/host-android-data/dalvik-cache
python3 "$TASK_DIR/repro/apply.py" --source "$SRC"
bash "$TASK_DIR/stage-device.sh"
cd "$SRC"
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 aapt zipalign signapk com.android.vcard
bash "$TASK_DIR/build-visual-shell.sh"
bash "$TASK_DIR/build-keypad-ime.sh"
sha256sum "$TASK_DIR/output/leo-visual.apk" "$TASK_DIR/output/leo-keypad-ime.apk"
