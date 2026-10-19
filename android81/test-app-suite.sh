#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$(mktemp -d /tmp/leo-app-tests.XXXXXX)"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
"$JAVA/javac" -encoding UTF-8 -d "$BUILD" "$TASK_DIR/visual-shell/UnitConversion.java" "$TASK_DIR/keypad-ime/PredictiveDictionary.java" "$TASK_DIR/app-tests/UnitConversionTest.java" "$TASK_DIR/app-tests/PredictiveDictionaryTest.java"
"$JAVA/java" -cp "$BUILD" ro.leo.visual.UnitConversionTest
"$JAVA/java" -cp "$BUILD" ro.leo.keypad.PredictiveDictionaryTest
