#!/bin/bash
# Build an isolated IMS candidate; dual-slot is opt-in. Never installs or starts it.
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
reference="$TASK_DIR/output/ims-reference/client"
module=LeoImsCandidate
name=leo-ims-candidate
output=ported-ims.apk
case "${1:-}" in
    '') ;;
    --dual-slot) module=LeoImsDualCandidate; name=leo-ims-dual-candidate; output=ported-ims-dual.apk;;
    *) echo 'Usage: build-ims-candidate.sh [--dual-slot]' >&2; exit 2;;
esac
target="/root/android81-leo/src/vendor/$name"
test -f "$reference/source-manifest.json"
if [ -e "$target" ]; then test -f "$target/leo-study-marker"; fi
mkdir -p "$target"
touch "$target/leo-study-marker"
trap 'if [ -f "$target/Android.mk" ]; then mv "$target/Android.mk" "$target/Android.mk.leo-study-disabled"; fi' EXIT
cp -r "$reference/src" "$reference/res" "$reference/AndroidManifest.xml" "$target/"
python3 "$TASK_DIR/port-ims-client.py" --root "$target/src"
if [ "${1:-}" = --dual-slot ]; then
    python3 "$TASK_DIR/patch-ims-dual-client.py" --root "$target"
fi
cat > "$target/Android.mk" <<EOF
LOCAL_PATH := \$(call my-dir)
include \$(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := $module
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_JAVA_LIBRARIES := telephony-common ims-common
LOCAL_PROTOC_OPTIMIZE_TYPE := micro
LOCAL_SRC_FILES := \$(call all-java-files-under,src/org/codeaurora/ims src/com/qualcomm/ims/vt) \$(call all-proto-files-under,src)
include \$(BUILD_PACKAGE)
EOF
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 "out/target/product/leo/obj/APPS/${module}_intermediates/package.apk"
cp "out/target/product/leo/obj/APPS/${module}_intermediates/package.apk" "$TASK_DIR/output/ims-reference/$output"
