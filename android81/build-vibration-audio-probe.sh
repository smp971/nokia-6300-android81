#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC=/root/android81-leo/src
BUILD="$(mktemp -d /tmp/leo-vibration-probe.XXXXXX)"
JAVA=/usr/lib/jvm/java-8-openjdk-amd64/bin
"$JAVA/javac" -encoding UTF-8 -source 1.8 -target 1.8 -d "$BUILD" "$TASK_DIR/VibrationAudioProbe.java"
"$JAVA/java" -jar "$SRC/prebuilts/sdk/tools/lib/dx.jar" --dex --output="$TASK_DIR/output/leo-vibration-audio-probe.dex" "$BUILD"
