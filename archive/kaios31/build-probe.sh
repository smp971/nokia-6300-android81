#!/bin/bash
set -euo pipefail
WORK=/mnt/d/ROMS/nokia-6300-ta1286-dump/kaios31
mkdir -p /root/kaios31-leo/probe
arm-linux-gnueabihf-as -march=armv7-a "$WORK/binder_probe.S" -o /root/kaios31-leo/probe/binder_probe.o
arm-linux-gnueabihf-ld -static --build-id=none -e _start \
    /root/kaios31-leo/probe/binder_probe.o -o "$WORK/binder-probe"
readelf -h "$WORK/binder-probe"
