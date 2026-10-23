#!/bin/bash
set -euo pipefail
SRC=/root/android81-leo/src
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SYSROOT="$SRC/prebuilts/ndk/r13/platforms/android-21/arch-arm"
"$SRC/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    --sysroot="$SYSROOT" -std=gnu99 -Wall -Wextra -Werror -Wno-attributes -fPIE -pie \
    -I"$SRC/bionic/libc/include" -I"$SRC/bionic/libc/kernel/uapi" \
    -I"$SRC/bionic/libc/kernel/uapi/asm-arm" -I"$SRC/bionic/libc/kernel/android/uapi" \
    -I"$SRC/bionic/libc/arch-arm/include" -D__ANDROID_API__=21 \
    "$TASK_DIR/camera-bootstrap-plan.c" -L"$SRC/out/target/product/leo/obj/lib" \
    -Wl,--export-dynamic -ldl -lc -o "$TASK_DIR/output/leo-camera-bootstrap-plan"
