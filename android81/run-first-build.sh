#!/bin/bash
set -euo pipefail
WORK=/mnt/d/ROMS/nokia-6300-ta1286-dump/android81
TASK=/root/android81-leo
mkdir -p "$TASK/logs"
exec 9>"$TASK/first-build.lock"
flock -n 9 || { echo 'Another first-build pipeline is active'; exit 1; }
PHASE=source-sync
trap 'printf "failed during %s; inspect WSL logs\n" "$PHASE" > "$WORK/first-build.state"' ERR
printf 'syncing-sources; network jobs=1, Git pack threads=1\n' > "$WORK/first-build.state"
bash "$WORK/sync-sources.sh"
PHASE=staging
bash "$WORK/stage-device.sh"
PHASE=diagnostic-build
printf 'building-native-diagnostic; make jobs=2\n' > "$WORK/first-build.state"
bash "$WORK/build-diag.sh" > "$TASK/logs/first-build.log" 2>&1
OUT="$TASK/src/out/target/product/leo"
test -s "$OUT/boot.img"
test -s "$OUT/system.img"
test "$(stat -c %s "$OUT/boot.img")" -le 33554432
test "$(stat -c %s "$OUT/system.img")" -le 838860800
mkdir -p "$WORK/output"
cp "$OUT/boot.img" "$OUT/system.img" "$WORK/output/"
sha256sum "$WORK/output/boot.img" "$WORK/output/system.img" > "$WORK/output/SHA256SUMS"
printf 'compiled; images untested on hardware; no flashing performed\n' > "$WORK/first-build.state"
