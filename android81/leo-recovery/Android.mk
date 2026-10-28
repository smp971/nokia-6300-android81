LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := leo-recovery
LOCAL_SRC_FILES := recovery.cpp framebuffer.cpp
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_CFLAGS := -Wall -Wextra -Werror -D_FILE_OFFSET_BITS=64
LOCAL_CPPFLAGS := -std=c++14
LOCAL_STATIC_LIBRARIES := libminui libziparchive libutils libcrypto libbase libpng libz liblog libcutils libc libm
LOCAL_MODULE_PATH := $(TARGET_RECOVERY_ROOT_OUT)/sbin
include $(BUILD_EXECUTABLE)
