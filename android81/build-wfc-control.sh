#!/bin/bash
set -euo pipefail
src=/root/android81-leo/src
project="$(cd "$(dirname "$0")" && pwd)"
sysroot="$src/prebuilts/ndk/r13/platforms/android-21/arch-arm"
"$src/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    --sysroot="$sysroot" -std=c99 -fPIE -pie -nostdlib -Wl,-dynamic-linker,/system/bin/linker \
    "$sysroot/usr/lib/crtbegin_dynamic.o" "$project/wfc-control.c" \
    -L"$src/out/target/product/leo/obj/lib" -lc -ldl \
    "$sysroot/usr/lib/crtend_android.o" -o "$project/output/wfc-control"
