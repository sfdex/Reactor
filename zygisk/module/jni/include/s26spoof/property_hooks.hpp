#pragma once

#include <stdint.h>
#include <jni.h>

#include "s26spoof/profile.hpp"

#if defined(__ANDROID__)
#include <sys/system_properties.h>
#else
struct prop_info;
#endif

namespace s26spoof {

inline constexpr int kPropertyValueCapacity = 92;

using PropertyGetFn = int (*)(const char *key, char *value);
using PropertyReadCallback =
    void (*)(void *cookie, const char *name, const char *value, uint32_t serial);
using PropertyReadCallbackFn =
    void (*)(const prop_info *info, PropertyReadCallback callback, void *cookie);
using JavaPropertyGetFn =
    jstring (*)(JNIEnv *environment, jclass type, jstring key, jstring default_value);

void set_active_profile(const DeviceProfile *profile) noexcept;
const DeviceProfile *get_active_profile() noexcept;

void set_original_property_get(PropertyGetFn function) noexcept;
void set_original_property_read_callback(PropertyReadCallbackFn function) noexcept;
void set_original_java_property_get(JavaPropertyGetFn function) noexcept;

int hooked_property_get(const char *key, char *value) noexcept;
void hooked_property_read_callback(const prop_info *info,
                                   PropertyReadCallback callback,
                                   void *cookie) noexcept;
jstring hooked_java_property_get(JNIEnv *environment, jclass type, jstring key,
                                 jstring default_value) noexcept;

}  // namespace s26spoof
