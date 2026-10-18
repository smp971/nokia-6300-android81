#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC=/root/android81-leo/src
BUILD=/root/android81-leo/radio-test
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
mkdir -p "$BUILD/classes"
"$SRC/out/host/linux-x86/bin/aapt" package -f -M "$TASK_DIR/radio-test/AndroidManifest.xml" -I "$SRC/prebuilts/sdk/27/android.jar" -F "$BUILD/unsigned.apk"
"$JAVA/javac" -source 1.8 -target 1.8 -bootclasspath "$SRC/prebuilts/sdk/27/android.jar" -d "$BUILD/classes" "$TASK_DIR/radio-test/RadioTest.java"
"$JAVA/java" -jar "$SRC/prebuilts/sdk/tools/lib/dx.jar" --dex --output="$BUILD/classes.dex" "$BUILD/classes"
(cd "$BUILD" && "$SRC/out/host/linux-x86/bin/aapt" add unsigned.apk classes.dex)
"$SRC/out/host/linux-x86/bin/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$JAVA/java" -Djava.library.path="$SRC/out/host/linux-x86/lib64" -jar "$SRC/out/host/linux-x86/framework/signapk.jar" "$SRC/build/target/product/security/platform.x509.pem" "$SRC/build/target/product/security/platform.pk8" "$BUILD/aligned.apk" "$TASK_DIR/output/radio-test.apk"
