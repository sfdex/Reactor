LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := zygisk
LOCAL_SRC_FILES := zygisk.cpp
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)