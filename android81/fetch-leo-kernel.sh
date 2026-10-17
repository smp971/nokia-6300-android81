#!/bin/bash
# Obtain the published Leo v20 kernel without fetching the Gecko source blobs.
set -euo pipefail
destination=/root/android81-leo/kernel-leo-v20
origin=https://github.com/bmndc/nokia-leo.git
revision=949a9f7b2c109f307e72b25fec2dc5cb4d119dec
if [ ! -e "$destination" ]; then
    git clone --depth 1 --single-branch --branch leo-v20 --filter=blob:none --sparse \
        "$origin" "$destination"
fi
test "$(git -C "$destination" remote get-url origin)" = "$origin"
test "$(git -C "$destination" rev-parse HEAD)" = "$revision"
git -C "$destination" sparse-checkout set kernel
head -5 "$destination/kernel/msm-4.9/Makefile"
printf 'Source: %s\nRevision: %s\n' "$destination" "$revision"
