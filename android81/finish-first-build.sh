#!/bin/bash
# Continue the already-running sync into a native diagnostic build. Never flashes.
set -euo pipefail
TASK=/root/android81-leo
WORK=/mnt/d/ROMS/nokia-6300-ta1286-dump/android81
SYNC_PID=${1:?Pass the existing sync-sources.sh process ID}
STATE="$WORK/first-build.state"
fail() {
    printf 'failed; see /root/android81-leo/logs/first-build.log\n' > "$STATE"
}
trap fail ERR
printf 'waiting-for-source-sync\n' > "$STATE"
EXPECTED='bash /mnt/d/ROMS/nokia-6300-ta1286-dump/android81/sync-sources.sh'
while [ "$(ps -p "$SYNC_PID" -o args= || true)" = "$EXPECTED" ]; do
    sleep 10
done
# sync-sources.sh writes a pinned manifest only after a successful sync.
test -s "$TASK/logs/manifest-pinned.xml"
printf 'staging-device\n' > "$STATE"
bash "$WORK/stage-device.sh"
printf 'building-native-diagnostic\n' > "$STATE"
bash "$WORK/build-diag.sh" > "$TASK/logs/first-build.log" 2>&1
OUT="$TASK/src/out/target/product/leo"
test -s "$OUT/boot.img"
test -s "$OUT/system.img"
test "$(stat -c %s "$OUT/boot.img")" -le 33554432
test "$(stat -c %s "$OUT/system.img")" -le 838860800
mkdir -p "$WORK/output"
cp "$OUT/boot.img" "$OUT/system.img" "$WORK/output/"
sha256sum "$WORK/output/boot.img" "$WORK/output/system.img" > "$WORK/output/SHA256SUMS"
printf 'compiled; images untested on hardware; no flashing performed\n' > "$STATE"
