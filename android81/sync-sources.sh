#!/bin/bash
set -euo pipefail
cd /root/android81-leo/src
# Keep Git pack processing bounded on the shared WSL VM.
export GIT_CONFIG_COUNT=2
export GIT_CONFIG_KEY_0=pack.threads
export GIT_CONFIG_VALUE_0=1
export GIT_CONFIG_KEY_1=pack.windowMemory
export GIT_CONFIG_VALUE_1=128m
# No force-sync: preserve any local development changes.
SYNC_LOG=/root/android81-leo/logs/repo-sync.log
trap 'tail -n 35 "$SYNC_LOG"' ERR
python3 /root/android81-leo/repo-launcher sync -c -j1 --no-tags --no-clone-bundle --fail-fast > "$SYNC_LOG" 2>&1
python3 /root/android81-leo/repo-launcher manifest -r -o /root/android81-leo/logs/manifest-pinned.xml
