#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="${LEO_AOSP_ROOT:-/root/android81-leo/src}"
JAVA="${LEO_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}/bin"
python3 "$TASK_DIR/localization/generate.py"
CLASSES="$TASK_DIR/output/host-organizer-classes"
mkdir -p "$CLASSES"
CP="$TASK_DIR/output/leo-visual-classes.jar:$SRC/prebuilts/sdk/27/android.jar"
"$JAVA/javac" -encoding UTF-8 -cp "$CP" -d "$CLASSES" "$SRC/libcore/json/src/main/java/org/json/"*.java "$TASK_DIR/host-tests/OrganizerAlgorithms.java" "$TASK_DIR/localization/L.java" "$TASK_DIR/localization/EnglishCatalog.java"
"$JAVA/java" -cp "$CLASSES:$CP" ro.leo.visual.OrganizerAlgorithms
