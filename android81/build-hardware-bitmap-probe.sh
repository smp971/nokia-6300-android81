#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC=/root/android81-leo/src
BUILD=/root/android81-leo/hardware-bitmap-probe
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
mkdir -p "$BUILD/classes" "$BUILD/assets"
python3 - "$BUILD/assets/probe.png" <<'PY'
import sys,struct,zlib
def chunk(kind,data):
    return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
row=b''.join(bytes((32,190,110,255)) if x<128 else bytes((32,110,220,255)) for x in range(256))
data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',256,256,8,6,0,0,0))
data+=chunk(b'IDAT',zlib.compress((b'\0'+row)*256))+chunk(b'IEND',b'')
open(sys.argv[1],'wb').write(data)
PY
"$SRC/out/host/linux-x86/bin/aapt" package -f -M "$TASK_DIR/device/nokia/leo/tests/AndroidManifest.xml" \
    -A "$BUILD/assets" -I "$SRC/prebuilts/sdk/27/android.jar" -F "$BUILD/base.apk"
"$JAVA/javac" -source 1.8 -target 1.8 -bootclasspath "$SRC/prebuilts/sdk/27/android.jar" \
    -d "$BUILD/classes" "$TASK_DIR/device/nokia/leo/tests/HardwareBitmapProbe.java"
"$JAVA/java" -jar "$SRC/prebuilts/sdk/tools/lib/dx.jar" --dex --output="$BUILD/classes.dex" "$BUILD/classes"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD" && "$SRC/out/host/linux-x86/bin/aapt" add unsigned.apk classes.dex)
"$SRC/out/host/linux-x86/bin/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$JAVA/java" -Djava.library.path="$SRC/out/host/linux-x86/lib64" -jar "$SRC/out/host/linux-x86/framework/signapk.jar" \
    "$SRC/build/target/product/security/testkey.x509.pem" "$SRC/build/target/product/security/testkey.pk8" \
    "$BUILD/aligned.apk" "$TASK_DIR/output/hardware-bitmap-probe.apk"
