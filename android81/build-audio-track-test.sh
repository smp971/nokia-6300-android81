#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC=/root/android81-leo/src
BUILD=/root/android81-leo/audio-track-test
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
mkdir -p "$BUILD/classes" "$BUILD/assets"
"$SRC/out/host/linux-x86/bin/aapt" package -f -M "$TASK_DIR/audio-test/AndroidManifest.xml" \
    -I "$SRC/prebuilts/sdk/27/android.jar" -F "$BUILD/base.apk"
"$JAVA/javac" -source 1.8 -target 1.8 -bootclasspath "$SRC/prebuilts/sdk/27/android.jar" \
    -d "$BUILD/classes" "$TASK_DIR/audio-test/AudioTest.java"
"$JAVA/java" -jar "$SRC/prebuilts/sdk/tools/lib/dx.jar" --dex --output="$BUILD/classes.dex" "$BUILD/classes"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD" && "$SRC/out/host/linux-x86/bin/aapt" add unsigned.apk classes.dex)
"$SRC/out/host/linux-x86/bin/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$JAVA/java" -Djava.library.path="$SRC/out/host/linux-x86/lib64" -jar "$SRC/out/host/linux-x86/framework/signapk.jar" \
    "$SRC/build/target/product/security/testkey.x509.pem" "$SRC/build/target/product/security/testkey.pk8" \
    "$BUILD/aligned.apk" "$TASK_DIR/output/audio-track-test.apk"
