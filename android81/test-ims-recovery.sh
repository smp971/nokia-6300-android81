#!/bin/bash
set -euo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$(mktemp -d /tmp/leo-ims-policy.XXXXXX)"
trap 'rm -rf "$BUILD"' EXIT
JAVA="${LEO_JAVA_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}/bin"
"$JAVA/javac" -encoding UTF-8 -d "$BUILD" "$TASK_DIR/ims-runtime/ImsRecoveryPolicy.java" "$TASK_DIR/ims-runtime/tests/RecoveryPolicyTest.java"
"$JAVA/java" -cp "$BUILD" org.codeaurora.ims.RecoveryPolicyTest
"$JAVA/javac" -encoding UTF-8 -d "$BUILD" "$TASK_DIR/ims-runtime/LeoImsTopology.java" "$TASK_DIR/ims-runtime/tests/TopologyTest.java"
"$JAVA/java" -cp "$BUILD" org.codeaurora.ims.TopologyTest
