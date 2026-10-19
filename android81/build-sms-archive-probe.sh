#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC=/root/android81-leo/src
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
BUILD="$(mktemp -d /root/android81-leo/sms-probe.XXXXXX)"
mkdir -p "$BUILD/classes"
"$JAVA/javac" -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$SRC/prebuilts/sdk/27/android.jar" \
    -classpath "$SRC/out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar" \
    -d "$BUILD/classes" "$TASK_DIR/visual-shell/SmsStore.java" "$TASK_DIR/visual-shell/SmsStatusReceiver.java" "$TASK_DIR/sms-tests/"*.java
"$JAVA/java" -jar "$SRC/prebuilts/sdk/tools/lib/dx.jar" --dex --output="$TASK_DIR/output/leo-sms-archive-probe.jar" "$BUILD/classes"
echo "Built isolated SMS archive probe; no SmsManager implementation is included"
