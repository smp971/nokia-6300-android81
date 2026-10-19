#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
SRC=/root/android81-leo/src
OVERLAY=device/nokia/leo/overlay/frameworks/base/core/res/res/values
mkdir -p "$SRC/$OVERLAY"
cp "$TASK_DIR/$OVERLAY/config.xml" "$SRC/$OVERLAY/config.xml"
cd "$SRC"
source build/envsetup.sh
lunch aosp_leo_go-eng
make -j4 framework-res
cp out/target/product/leo/system/framework/framework-res.apk "$TASK_DIR/output/framework-res-connectivity.apk"
