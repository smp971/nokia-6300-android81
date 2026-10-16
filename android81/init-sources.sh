#!/bin/bash
set -euo pipefail
TASK_DIR=/root/android81-leo
mkdir -p "$TASK_DIR/src" "$TASK_DIR/host-bin" "$TASK_DIR/logs"
# Python 2 is local to this old build environment; system Python is untouched.
ln -sfn /usr/bin/python2 "$TASK_DIR/host-bin/python"
cd "$TASK_DIR/src"
export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0=user.name
export GIT_CONFIG_VALUE_0="Leo Android Builder"
export GIT_CONFIG_KEY_1=user.email
export GIT_CONFIG_VALUE_1=leo-builder@localhost
python3 "$TASK_DIR/repo-launcher" init -u https://android.googlesource.com/platform/manifest \
    -b android-8.1.0_r81 --depth=1 --no-clone-bundle \
    --repo-rev=stable < /dev/null
python3 "$TASK_DIR/repo-launcher" manifest -o "$TASK_DIR/logs/manifest-initial.xml"
