#!/bin/bash
set -euo pipefail
SOURCE_DEVICE=/mnt/d/ROMS/nokia-6300-ta1286-dump/android81/device/nokia/leo
TARGET_DEVICE=/root/android81-leo/src/device/nokia/leo
mkdir -p "$TARGET_DEVICE"
# Explicit staging without deletion. Canonical edited files stay in the workspace.
rsync -a "$SOURCE_DEVICE/" "$TARGET_DEVICE/"
