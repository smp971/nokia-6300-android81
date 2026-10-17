#!/bin/bash
set -euo pipefail
src=/root/android81-leo/src
project="$(cd "$(dirname "$0")" && pwd)"
ndk="$src/prebuilts/ndk/r13/platforms/android-21/arch-arm/usr"
"$src/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    -std=c99 -Wall -Wextra -Werror -fPIE -pie -nostdlib -Wl,-dynamic-linker,/system/bin/linker -Wl,--export-dynamic \
    -D__ANDROID_API__=27 -isystem "$src/bionic/libc/include" \
    -isystem "$src/bionic/libc/kernel/uapi/asm-arm" -isystem "$src/bionic/libc/kernel/uapi" \
    -isystem "$src/bionic/libc/kernel/android/uapi" \
    -I"$src/hardware/libhardware/include" -I"$src/system/core/include" \
    -I"$src/frameworks/native/libs/nativebase/include" \
    "$ndk/lib/crtbegin_dynamic.o" "$project/adreno-buffer-probe.c" \
    -L"$src/out/target/product/leo/obj/lib" -lc -ldl \
    "$ndk/lib/crtend_android.o" -o "$project/output/adreno-buffer-probe"
"$src/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    -std=c99 -Wall -Wextra -Werror -shared -fPIC -nostdlib -D__ANDROID_API__=27 \
    -isystem "$src/bionic/libc/include" -isystem "$src/bionic/libc/kernel/uapi/asm-arm" \
    -isystem "$src/bionic/libc/kernel/uapi" -isystem "$src/bionic/libc/kernel/android/uapi" \
    -I"$src/hardware/libhardware/include" -I"$src/system/core/include" \
    "$project/gralloc-adreno-bridge.c" -L"$src/out/target/product/leo/obj/lib" -lc -ldl \
    -o "$project/output/gralloc-adreno-bridge.so"
