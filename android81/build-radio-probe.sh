#!/bin/bash
set -euo pipefail
src=/root/android81-leo/src
project=/mnt/d/ROMS/nokia-6300-ta1286-dump/android81
crt="$src/prebuilts/ndk/r13/platforms/android-21/arch-arm/usr/lib"
"$src/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    -std=c99 -fPIE -pie -nostdlib -Wl,-dynamic-linker,/system/bin/linker \
    "$crt/crtbegin_dynamic.o" "$project/radio-library-probe.c" \
    -L"$src/out/target/product/leo/obj/lib" -lc -ldl \
    "$crt/crtend_android.o" -o "$project/output/radio-library-probe"
"$src/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    -shared -fPIC -nostdlib "$project/radio-ipc-shim.c" \
    -L"$src/out/target/product/leo/obj/lib" -lc -ldl -llog \
    -o "$project/output/radio-ipc-shim.so"
"$src/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
    -std=c99 -fPIE -pie -nostdlib -Wl,-dynamic-linker,/system/bin/linker \
    "$crt/crtbegin_dynamic.o" "$project/radio-dms-probe.c" \
    -L"$src/out/target/product/leo/obj/lib" -lc -ldl \
    "$crt/crtend_android.o" -o "$project/output/radio-dms-probe"
