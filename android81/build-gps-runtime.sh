#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
cd "${LEO_AOSP_ROOT:-/root/android81-leo/src}"
source build/envsetup.sh
lunch aosp_leo_go-eng
make -j4 android.hardware.gnss@1.0-service android.hardware.gnss@1.0-impl

bash "$TASK_DIR/build-gps-runtime-exec.sh"
