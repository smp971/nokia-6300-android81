#!/bin/bash
set -euo pipefail
SOURCE_DEVICE="$(cd "$(dirname "$0")" && pwd)/device/nokia/leo"
TARGET_DEVICE=/root/android81-leo/src/device/nokia/leo
mkdir -p "$TARGET_DEVICE"
# Explicit staging without deletion. Canonical edited files stay in the workspace.
rsync -a "$SOURCE_DEVICE/" "$TARGET_DEVICE/"
