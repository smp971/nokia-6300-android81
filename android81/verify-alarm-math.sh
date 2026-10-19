#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$(mktemp -d /tmp/leo-alarm-test.XXXXXX)"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
"$JAVA/javac" -encoding UTF-8 -d "$BUILD" "$TASK_DIR/visual-shell/AlarmMath.java" "$TASK_DIR/visual-shell/tests/AlarmMathTest.java"
"$JAVA/java" -cp "$BUILD" ro.leo.visual.AlarmMathTest
