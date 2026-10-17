#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
bash "$TASK_DIR/stage-device.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 leo_audio_probe
cp out/target/product/leo/system/bin/leo_audio_probe "$TASK_DIR/output/leo-audio-probe"
