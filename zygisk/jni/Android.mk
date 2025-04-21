LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := zygisk
LOCAL_SRC_FILES := zygisk.cpp hook.cpp hook/system_property.cpp hook/zygisk_info.cpp
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)