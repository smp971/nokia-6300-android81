#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
SRC=/root/android81-leo/src
mkdir -p "$SRC/device/nokia/leo/fm-route"
cp "$TASK_DIR/fm-runtime/Android.mk" "$TASK_DIR/fm-runtime/fm-route.cpp" "$SRC/device/nokia/leo/fm-route/"
cd "$SRC"
source build/envsetup.sh
lunch aosp_leo_go-eng
mmm device/nokia/leo/fm-route -j4
cp out/target/product/leo/system/bin/leo-fm-route "$TASK_DIR/output/leo-fm-route"
