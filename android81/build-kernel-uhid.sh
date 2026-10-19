#!/bin/bash
# Offline build only. Does not package or flash boot/recovery.
set -euo pipefail
work=/mnt/d/ROMS/nokia-6300-ta1286-dump/android81
source_tree=/root/android81-leo/kernel-leo-v20/kernel/msm-4.9
build=/root/android81-leo/kernel-uhid-build
cross=/root/android81-leo/src/prebuilts/gcc/linux-x86/arm/arm-eabi-4.8/bin/arm-eabi-
test "$(git -C "$source_tree" rev-parse HEAD)" = 949a9f7b2c109f307e72b25fec2dc5cb4d119dec
test -s "$work/output/kernel-uhid/stock-trusted.pem"
mkdir -p "$build" "$work/output/kernel-uhid"
if [ ! -f "$build/.leo-uhid-configured" ]; then
    tr -d '\r' < "$work/device/nokia/leo/reference-stock/kernel.config" > "$build/.config"
    cp "$work/output/kernel-uhid/stock-trusted.pem" "$build/stock-trusted.pem"
    "$source_tree/scripts/config" --file "$build/.config" --enable UHID \
        --disable LOCALVERSION_AUTO --set-str SYSTEM_TRUSTED_KEYS "$build/stock-trusted.pem"
    touch "$build/.leo-uhid-configured"
fi
export ARCH=arm CROSS_COMPILE="$cross" LOCALVERSION=-g4f9e270fd77f
export KBUILD_BUILD_USER=leo-builder KBUILD_BUILD_HOST=android81-wsl
make -C "$source_tree" O="$build" olddefconfig
cp "$build/.config" "$work/output/kernel-uhid/config"
make -C "$source_tree" O="$build" -j4 zImage modules dtbs
cp "$build/Module.symvers" "$build/System.map" "$build/arch/arm/boot/zImage" \
    "$work/output/kernel-uhid/"
sha256sum "$work/output/kernel-uhid/zImage" > "$work/output/kernel-uhid/SHA256SUMS"
printf 'Offline candidate built. Module ABI and certificate checks are still required.\n'
