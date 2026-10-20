#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
python3 "$TASK_DIR/patch-deskclock-background-stop.py"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_go-eng
make DeskClock -j4
cp out/target/product/leo/system/app/DeskClock/DeskClock.apk "$TASK_DIR/output/deskclock-background-stop.apk"
