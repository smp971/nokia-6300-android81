#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
make -j4 android.hardware.camera.provider@2.4-service android.hardware.camera.provider@2.4-impl camera.device@1.0-impl camera.device@3.2-impl camera.device@3.3-impl
