#!/bin/bash
# Compatibility build only. Does not install or start the downloaded client.
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
reference="$TASK_DIR/output/ims-reference/client"
target=/root/android81-leo/src/vendor/leo-ims-candidate
test -f "$reference/source-manifest.json"
if [ -e "$target" ]; then test -f "$target/leo-study-marker"; fi
mkdir -p "$target"
touch "$target/leo-study-marker"
trap 'if [ -f "$target/Android.mk" ]; then mv "$target/Android.mk" "$target/Android.mk.leo-study-disabled"; fi' EXIT
cp -r "$reference/src" "$reference/res" "$reference/AndroidManifest.xml" "$target/"
cat > "$target/Android.mk" <<'EOF'
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := LeoImsCandidate
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_JAVA_LIBRARIES := telephony-common ims-common
LOCAL_PROTOC_OPTIMIZE_TYPE := micro
LOCAL_SRC_FILES := $(call all-java-files-under,src/org/codeaurora/ims src/com/qualcomm/ims/vt) $(call all-proto-files-under,src)
include $(BUILD_PACKAGE)
EOF
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 out/target/common/obj/APPS/LeoImsCandidate_intermediates/package.apk
