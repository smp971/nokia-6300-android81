#!/bin/bash
set -euo pipefail
SRC=/root/android81-leo/src
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SYSROOT="$SRC/prebuilts/ndk/r13/platforms/android-21/arch-arm"
"$SRC/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    --sysroot="$SYSROOT" -std=c99 -Wall -Wextra -Werror -fPIE -pie -nostdlib \
    -Wl,-dynamic-linker,/system/bin/linker "$SYSROOT/usr/lib/crtbegin_dynamic.o" \
    "$TASK_DIR/usb-adbd-renew.c" -L"$SRC/out/target/product/leo/obj/lib" -lc \
    "$SYSROOT/usr/lib/crtend_android.o" -o "$TASK_DIR/output/leo-adbd-renew"
