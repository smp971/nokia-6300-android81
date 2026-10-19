#!/bin/bash
# Build an ELF object for offline inspection; never install or load it.
set -euo pipefail
work=/mnt/d/ROMS/nokia-6300-ta1286-dump/android81
probe=/root/android81-leo/audio-abi-probe
mkdir -p "$probe" "$work/output/audio-abi"
cp "$work/audio-abi/leo_audio_abi.c" "$probe/"
printf 'obj-m += leo_audio_abi.o\n' > "$probe/Makefile"
make -C /root/android81-leo/kernel-leo-v20/kernel/msm-4.9 \
    O=/root/android81-leo/kernel-uhid-build M="$probe" ARCH=arm \
    CROSS_COMPILE=/root/android81-leo/src/prebuilts/gcc/linux-x86/arm/arm-eabi-4.8/bin/arm-eabi- \
    LOCALVERSION=-g4f9e270fd77f leo_audio_abi.o
cp "$probe/leo_audio_abi.o" "$work/output/audio-abi/"
