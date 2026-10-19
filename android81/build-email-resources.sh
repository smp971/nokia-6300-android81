#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
SRC=/root/android81-leo/src
python3 "$TASK_DIR/patch-email-resources.py" --source "$SRC"
cd "$SRC"
source build/envsetup.sh
lunch aosp_leo_go-eng
make -j4 Email
cp out/target/product/leo/system/app/Email/Email.apk "$TASK_DIR/output/Email-leo.apk"
sha256sum "$TASK_DIR/output/Email-leo.apk"
