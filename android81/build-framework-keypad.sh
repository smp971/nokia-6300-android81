#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
SRC="${LEO_AOSP_ROOT:-/root/android81-leo/src}"
PATCH_ARGS=()
if [ "${1:-}" = "--apps-only" ]; then PATCH_ARGS+=(--apps-only); fi
python3 "$TASK_DIR/patch-framework-keypad.py" --source "$SRC" "${PATCH_ARGS[@]}"
cd "$SRC"
source build/envsetup.sh
lunch aosp_leo_go-eng
if [ "${1:-}" = "--apps-only" ]; then
    make -j4 SystemUI Settings PackageInstaller
else
    make -j4 framework SystemUI Settings PackageInstaller
fi
mkdir -p "$TASK_DIR/output/framework-keypad"
if [ "${1:-}" != "--apps-only" ]; then
    cp out/target/product/leo/system/framework/framework.jar "$TASK_DIR/output/framework-keypad/framework.jar"
fi
cp out/target/product/leo/system/priv-app/SystemUI/SystemUI.apk "$TASK_DIR/output/framework-keypad/SystemUI.apk"
cp out/target/product/leo/system/priv-app/Settings/Settings.apk "$TASK_DIR/output/framework-keypad/Settings.apk"
cp out/target/product/leo/system/priv-app/PackageInstaller/PackageInstaller.apk "$TASK_DIR/output/framework-keypad/PackageInstaller.apk"
sha256sum "$TASK_DIR/output/framework-keypad/"*.apk
if [ "${1:-}" != "--apps-only" ]; then
    sha256sum "$TASK_DIR/output/framework-keypad/framework.jar"
fi
