#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
bash "$TASK_DIR/stage-device.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_diag-eng
make -j2 leo_diag_ui
cp out/target/product/leo/system/bin/leo_diag_ui "$TASK_DIR/output/leo-diag-ui"
