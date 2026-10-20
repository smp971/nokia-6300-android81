LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := leo-fm-route
LOCAL_SRC_FILES := fm-route.cpp
LOCAL_SHARED_LIBRARIES := libaudioclient libutils liblog
LOCAL_CFLAGS := -Wall -Wextra -Werror
include $(BUILD_EXECUTABLE)
