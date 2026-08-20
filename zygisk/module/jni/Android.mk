LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := s26spoof
LOCAL_SRC_FILES := \
    core.cpp \
    maps.cpp \
    property_hooks.cpp \
    native_hooks.cpp \
    build_fields.cpp \
    config_parser.cpp \
    companion.cpp \
    lifecycle.cpp \
    module.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CPPFLAGS := \
    -Wall \
    -Wextra \
    -Werror \
    -fvisibility=hidden \
    -fvisibility-inlines-hidden \
    -ffunction-sections \
    -fdata-sections
LOCAL_LDFLAGS := -Wl,--gc-sections -Wl,--build-id=none
LOCAL_LDLIBS := -llog -ldl
include $(BUILD_SHARED_LIBRARY)
