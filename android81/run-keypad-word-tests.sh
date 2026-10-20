#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
BUILD="$(mktemp -d /tmp/leo-word-tests.XXXXXX)"
"$JAVA/javac" -encoding UTF-8 -d "$BUILD" "$TASK_DIR/keypad-ime/PredictiveDictionary.java" "$TASK_DIR/keypad-ime/PersonalWords.java" "$TASK_DIR/app-tests/PredictiveDictionaryTest.java" "$TASK_DIR/app-tests/PersonalWordsTest.java"
"$JAVA/java" -cp "$BUILD" ro.leo.keypad.PredictiveDictionaryTest
"$JAVA/java" -cp "$BUILD" ro.leo.keypad.PersonalWordsTest
