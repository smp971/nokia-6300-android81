#!/bin/bash
set -euo pipefail
src=/root/android81-leo/src
project="$(cd "$(dirname "$0")" && pwd)"
crt="$src/prebuilts/ndk/r13/platforms/android-21/arch-arm/usr/lib"
"$src/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    -std=c99 -Wall -Wextra -Werror -fPIE -pie -nostdlib -Wl,-dynamic-linker,/system/bin/linker \
    "$crt/crtbegin_dynamic.o" "$project/adreno-probe.c" \
    -L"$src/out/target/product/leo/obj/lib" -lc -ldl \
    "$crt/crtend_android.o" -o "$project/output/adreno-probe"
