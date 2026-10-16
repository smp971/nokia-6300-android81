#!/bin/bash
set -eo pipefail
TASK_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$TASK_DIR/environment.sh"
bash "$TASK_DIR/stage-device.sh"
cd /root/android81-leo/src
source build/envsetup.sh
lunch aosp_leo_diag-eng
make -j2 leo_graphics_probe gralloc.default libEGL_swiftshader libGLESv1_CM_swiftshader libGLESv2_swiftshader \
  leo_graphics_exec hwcomposer.leo leo_surface_test screencap \
  surfaceflinger servicemanager hwservicemanager logd logcat dumpsys service \
  android.hardware.graphics.composer@2.1-impl android.hardware.graphics.composer@2.1-service \
  android.hardware.graphics.allocator@2.0-impl android.hardware.graphics.allocator@2.0-service \
  android.hardware.graphics.mapper@2.0-impl
