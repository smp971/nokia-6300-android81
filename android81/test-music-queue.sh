#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$(mktemp -d /tmp/leo-queue-tests.XXXXXX)"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
"$JAVA/javac" -encoding UTF-8 -d "$BUILD" "$TASK_DIR/visual-shell/MusicQueue.java" "$TASK_DIR/app-tests/MusicQueueTest.java"
"$JAVA/java" -cp "$BUILD" ro.leo.visual.MusicQueueTest
