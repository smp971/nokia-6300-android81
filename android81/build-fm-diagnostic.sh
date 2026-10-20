#!/bin/bash
set -euo pipefail
SRC=/root/android81-leo/src
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SYSROOT="$SRC/prebuilts/ndk/r13/platforms/android-21/arch-arm"
for target in fm-diagnostic fm-trial; do
"$SRC/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
  --sysroot="$SYSROOT" -std=gnu99 -Wall -Wextra -Werror -fPIE -pie \
  -Wno-attributes -D__ANDROID_API__=21 -I"$SRC/bionic/libc/include" -I"$SRC/bionic/libc/kernel/uapi" -I"$SRC/bionic/libc/kernel/uapi/asm-arm" -I"$SRC/bionic/libc/kernel/android/uapi" -I"$SRC/bionic/libc/arch-arm/include" \
  -L"$SRC/out/target/product/leo/obj/lib" -lc "$TASK_DIR/fm-runtime/$target.c" -o "$TASK_DIR/output/leo-$target"
done

"$SRC/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-gcc" \
  --sysroot="$SYSROOT" -std=gnu99 -Wall -Wextra -Werror -fPIC -shared \
  -Wno-attributes -D__ANDROID_API__=21 -I"$SRC/bionic/libc/include" -I"$SRC/bionic/libc/kernel/uapi" -I"$SRC/bionic/libc/kernel/uapi/asm-arm" -I"$SRC/bionic/libc/kernel/android/uapi" -I"$SRC/bionic/libc/arch-arm/include" \
  -L"$SRC/out/target/product/leo/obj/lib" -lc -ldl "$TASK_DIR/fm-runtime/fm-trace.c" -o "$TASK_DIR/output/leo-fm-trace.so"

BUILD="$(mktemp -d /tmp/leo-fm-audio.XXXXXX)"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
mkdir -p "$BUILD/classes"
"$JAVA/javac" -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$SRC/prebuilts/sdk/27/android.jar" -d "$BUILD/classes" "$TASK_DIR/fm-runtime/FmAudioCommand.java"
"$JAVA/java" -jar "$SRC/prebuilts/sdk/tools/lib/dx.jar" --dex --output="$TASK_DIR/output/leo-fm-audio.jar" "$BUILD/classes"
