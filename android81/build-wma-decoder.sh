#!/bin/bash
# Minimal LGPL FFmpeg CLI: local ASF/WMA -> PCM only, no network or encoders
# except PCM. The original source stays outside the repository.
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC=/root/android81-leo/src
base=/root/android81-leo/ffmpeg-wma
version=4.4.8
mkdir -p "$base"
cd "$base"
if [ ! -f "ffmpeg-$version.tar.xz" ]; then
    curl --fail --location "https://ffmpeg.org/releases/ffmpeg-$version.tar.xz" -o "ffmpeg-$version.tar.xz"
fi
printf '%s  %s\n' c73848c4ae283d9eaee7be3b276affbc3543380483555500d0dd2c9b7e1c39c3 "ffmpeg-$version.tar.xz" | sha256sum -c -
if [ ! -f "ffmpeg-$version/configure" ]; then
    tar xf "ffmpeg-$version.tar.xz"
fi
cd "ffmpeg-$version"
toolchain="$SRC/prebuilts/gcc/linux-x86/arm/arm-linux-androideabi-4.9/bin/arm-linux-androideabi-"
sysroot="$SRC/prebuilts/ndk/r13/platforms/android-21/arch-arm"
includes="-I$SRC/bionic/libc/include -I$SRC/bionic/libc/kernel/uapi -I$SRC/bionic/libc/kernel/uapi/asm-arm -I$SRC/bionic/libc/kernel/android/uapi -I$SRC/bionic/libc/arch-arm/include"
./configure --target-os=android --arch=arm --cpu=cortex-a7 --enable-cross-compile \
    --cross-prefix="$toolchain" --cc="${toolchain}gcc" --sysroot="$sysroot" --disable-everything \
    --disable-autodetect --disable-network --disable-doc --disable-debug \
    --disable-shared --enable-static --enable-ffmpeg --disable-ffplay --disable-ffprobe \
    --enable-protocol=file,pipe --enable-demuxer=asf --enable-decoder=wmav1,wmav2,wmapro,wmalossless \
    --enable-encoder=pcm_s16le --enable-muxer=s16le,wav --enable-filter=aresample,anull \
    --extra-cflags="$includes -D__ANDROID_API__=21 -fPIE -Wno-attributes" \
    --extra-ldflags="-pie -L$SRC/out/target/product/leo/obj/lib -Wl,-rpath-link,$SRC/out/target/product/leo/obj/lib"
make -j4 ffmpeg
cp ffmpeg "$TASK_DIR/output/leo-wma-decode"
cp COPYING.LGPLv2.1 "$TASK_DIR/output/leo-wma-LICENSE.txt"
sha256sum "../ffmpeg-$version.tar.xz" "$TASK_DIR/output/leo-wma-decode" > "$TASK_DIR/output/wma-build-sha256.txt"
