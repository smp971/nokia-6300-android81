#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA="${LEO_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}/bin"
python3 "$TASK_DIR/localization/generate.py"
CLASSES="$TASK_DIR/output/host-keypad-web-classes"
mkdir -p "$CLASSES"
"$JAVA/javac" -encoding UTF-8 -d "$CLASSES" "$TASK_DIR/keypad-ime/PredictiveDictionary.java" "$TASK_DIR/keypad-ime/PersonalWords.java" "$TASK_DIR/visual-shell/BrowserRules.java" "$TASK_DIR/host-tests/KeypadAlgorithms.java" "$TASK_DIR/host-tests/BrowserAlgorithms.java" "$TASK_DIR/localization/L.java" "$TASK_DIR/localization/EnglishCatalog.java"
"$JAVA/java" -cp "$CLASSES" ro.leo.keypad.KeypadAlgorithms
"$JAVA/java" -cp "$CLASSES" ro.leo.visual.BrowserAlgorithms
