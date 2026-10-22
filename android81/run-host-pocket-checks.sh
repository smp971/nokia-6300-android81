#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="${LEO_AOSP_ROOT:-/root/android81-leo/src}"
JAVA="${LEO_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}/bin"
CLASSES="$TASK_DIR/output/host-pocket-classes"
mkdir -p "$CLASSES"
"$JAVA/javac" -encoding UTF-8 -d "$CLASSES" "$SRC/libcore/json/src/main/java/org/json/"*.java "$TASK_DIR/visual-shell/BookDocument.java" "$TASK_DIR/visual-shell/PocketGames.java" "$TASK_DIR/visual-shell/AppBundle.java" "$TASK_DIR/host-tests/PocketAlgorithms.java"
"$JAVA/java" -cp "$CLASSES" ro.leo.visual.PocketAlgorithms
