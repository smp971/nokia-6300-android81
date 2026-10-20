#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
make audio.a2dp.default -j4
cp out/target/product/leo/system/lib/hw/audio.a2dp.default.so "$TASK_DIR/output/audio.a2dp.default.so"
