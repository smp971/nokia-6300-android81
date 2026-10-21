#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="${LEO_AOSP_ROOT:-/root/android81-leo/src}"
BUILD="$(mktemp -d "${TMPDIR:-/tmp}/leo-keypad-build.XXXXXX")"
JAVA="${LEO_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}/bin"
mkdir -p "$BUILD/classes"
"$SRC/out/host/linux-x86/bin/aapt" package -f -M "$TASK_DIR/keypad-ime/AndroidManifest.xml" -S "$TASK_DIR/keypad-ime/res" -I "$SRC/prebuilts/sdk/27/android.jar" -F "$BUILD/base.apk"
"$JAVA/javac" -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$SRC/prebuilts/sdk/27/android.jar" -d "$BUILD/classes" "$TASK_DIR/keypad-ime/"*.java
"$JAVA/jar" cf "$TASK_DIR/output/leo-keypad-classes.jar" -C "$BUILD/classes" .
"$JAVA/java" -jar "$SRC/prebuilts/sdk/tools/lib/dx.jar" --dex --output="$BUILD/classes.dex" "$BUILD/classes"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD" && "$SRC/out/host/linux-x86/bin/aapt" add unsigned.apk classes.dex)
"$SRC/out/host/linux-x86/bin/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$JAVA/java" -Djava.library.path="$SRC/out/host/linux-x86/lib64" -jar "$SRC/out/host/linux-x86/framework/signapk.jar" "$SRC/build/target/product/security/testkey.x509.pem" "$SRC/build/target/product/security/testkey.pk8" "$BUILD/aligned.apk" "$TASK_DIR/output/leo-keypad-ime.apk"
sha256sum "$TASK_DIR/output/leo-keypad-ime.apk"
