#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/patch-netpolicy-boot.py"
python3 "$TASK_DIR/patch-leo-policy-access.py"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
export ANDROID_DATA=/root/android81-leo/host-android-data
make -j4 services
cp out/target/product/leo/system/framework/services.jar "$TASK_DIR/output/services-netpolicy.jar"
mkdir -p "$TASK_DIR/output/netpolicy-runtime"
cp out/target/product/leo/system/framework/services.jar "$TASK_DIR/output/netpolicy-runtime/"
for extension in art odex vdex; do
    cp "out/target/product/leo/system/framework/oat/arm/services.$extension" "$TASK_DIR/output/netpolicy-runtime/"
done
python3 "$TASK_DIR/collect-netpolicy-runtime.py"
sha256sum "$TASK_DIR/output/services-netpolicy.jar"
