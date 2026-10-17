LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := leo_framework_exec
LOCAL_SRC_FILES := framework-exec.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc libm libdl
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := leo_framework_mount
LOCAL_SRC_FILES := framework-mount.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc libm libdl
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := leo_diag_bootstrap
LOCAL_SRC_FILES := diag-bootstrap.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc libm libdl
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := leo_diag_ui
LOCAL_SRC_FILES := diag-ui.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc libm libdl
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := leo_graphics_probe
LOCAL_SRC_FILES := graphics-probe.cpp
LOCAL_SHARED_LIBRARIES := libEGL libGLESv2 libhardware libcutils liblog
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := leo_graphics_exec
LOCAL_SRC_FILES := graphics-exec.c
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_STATIC_LIBRARIES := libc libm libdl
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := hwcomposer.leo
LOCAL_SRC_FILES := hwcomposer-leo.cpp
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_PROPRIETARY_MODULE := true
LOCAL_SHARED_LIBRARIES := libhardware liblog libcutils libsync
LOCAL_HEADER_LIBRARIES := libhardware_headers
LOCAL_CFLAGS := -Wall -Wextra -Werror -Wno-missing-field-initializers
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := leo_surface_test
LOCAL_SRC_FILES := surface-test.cpp
LOCAL_SHARED_LIBRARIES := libgui libui libutils libbinder libnativewindow liblog
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := leo_audio_probe
LOCAL_SRC_FILES := audio-hal-probe.c
LOCAL_SHARED_LIBRARIES := libdl
LOCAL_HEADER_LIBRARIES := libhardware_headers
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)
