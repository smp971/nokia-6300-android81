#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="${LEO_AOSP_ROOT:-/root/android81-leo/src}"
BUILD="$(mktemp -d "${TMPDIR:-/tmp}/leo-visual-build.XXXXXX")"
JAVA="${LEO_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}/bin"
VCARD="$SRC/out/target/common/obj/JAVA_LIBRARIES/com.android.vcard_intermediates/classes.jar"
D8="${LEO_D8_JAR:-$SRC/prebuilts/sdk/tools/lib/d8.jar}"
test -f "$VCARD"
if [ ! -f "$D8" ]; then echo "Set LEO_D8_JAR to build-tools/28.0.3/lib/d8.jar" >&2; exit 1; fi
mkdir -p "$BUILD/classes"
python3 "$TASK_DIR/localization/generate.py"
python3 "$TASK_DIR/prepare-pocket-dependencies.py" --output "$BUILD/dependencies"
"$SRC/out/host/linux-x86/bin/aapt" package -f -M "$TASK_DIR/visual-shell/AndroidManifest.xml" -S "$TASK_DIR/visual-shell/res" -A "$TASK_DIR/visual-shell/assets" -I "$SRC/prebuilts/sdk/27/android.jar" -F "$BUILD/base.apk"
"$JAVA/javac" -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$SRC/prebuilts/sdk/27/android.jar" -classpath "$VCARD:$BUILD/dependencies/jsch.jar" -d "$BUILD/classes" "$TASK_DIR/visual-shell/"*.java "$TASK_DIR/localization/"*.java "$TASK_DIR/third-party/pocket/"*.java
"$JAVA/jar" cf "$TASK_DIR/output/leo-visual-classes.jar" -C "$BUILD/classes" .
mkdir -p "$BUILD/dex"
"$JAVA/java" -cp "$D8" com.android.tools.r8.D8 --min-api 27 --lib "$SRC/prebuilts/sdk/27/android.jar" --output "$BUILD/dex" "$TASK_DIR/output/leo-visual-classes.jar" "$BUILD/dependencies/jsch.jar" "$VCARD"
cp "$BUILD/dex/classes.dex" "$BUILD/classes.dex"
cp "$BUILD/base.apk" "$BUILD/unsigned.apk"
(cd "$BUILD" && "$SRC/out/host/linux-x86/bin/aapt" add unsigned.apk classes.dex)
"$SRC/out/host/linux-x86/bin/zipalign" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
"$JAVA/java" -Djava.library.path="$SRC/out/host/linux-x86/lib64" -jar "$SRC/out/host/linux-x86/framework/signapk.jar" "$SRC/build/target/product/security/testkey.x509.pem" "$SRC/build/target/product/security/testkey.pk8" "$BUILD/aligned.apk" "$TASK_DIR/output/leo-visual.apk"
sha256sum "$TASK_DIR/output/leo-visual.apk"
