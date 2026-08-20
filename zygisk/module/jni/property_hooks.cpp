#include "s26spoof/property_hooks.hpp"

#include "s26spoof/core.hpp"

namespace s26spoof {
namespace {

const DeviceProfile *active_profile = nullptr;
PropertyGetFn original_property_get = nullptr;
PropertyReadCallbackFn original_property_read_callback = nullptr;
JavaPropertyGetFn original_java_property_get = nullptr;

struct CallbackContext {
    PropertyReadCallback callback;
    void *cookie;
};

void override_read_callback(void *raw_context, const char *name, const char *value,
                            uint32_t serial) {
    auto *context = static_cast<CallbackContext *>(raw_context);
    if (context == nullptr || context->callback == nullptr) return;
    const char *override = (active_profile != nullptr) ? find_property_override(*active_profile, name) : nullptr;
    context->callback(context->cookie, name, override == nullptr ? value : override, serial);
}

}  // namespace

void set_active_profile(const DeviceProfile *profile) noexcept {
    active_profile = profile;
}

const DeviceProfile *get_active_profile() noexcept {
    return active_profile;
}

void set_original_property_get(PropertyGetFn function) noexcept {
    original_property_get = function;
}

void set_original_property_read_callback(PropertyReadCallbackFn function) noexcept {
    original_property_read_callback = function;
}

void set_original_java_property_get(JavaPropertyGetFn function) noexcept {
    original_java_property_get = function;
}

int hooked_property_get(const char *key, char *value) noexcept {
    if (value == nullptr) return 0;
    const char *override = (active_profile != nullptr) ? find_property_override(*active_profile, key) : nullptr;
    if (override != nullptr) {
        const int copied = copy_property_value(override, value, kPropertyValueCapacity);
        return copied < 0 ? 0 : copied;
    }
    if (original_property_get != nullptr) return original_property_get(key, value);
    value[0] = '\0';
    return 0;
}

void hooked_property_read_callback(const prop_info *info,
                                   PropertyReadCallback callback,
                                   void *cookie) noexcept {
    if (original_property_read_callback == nullptr || callback == nullptr) return;
    CallbackContext context{callback, cookie};
    original_property_read_callback(info, override_read_callback, &context);
}

jstring hooked_java_property_get(JNIEnv *environment, jclass type, jstring key,
                                 jstring default_value) noexcept {
    const auto delegate = [&]() -> jstring {
        if (original_java_property_get != nullptr) {
            return original_java_property_get(environment, type, key, default_value);
        }
        return default_value;
    };

    if (environment == nullptr) return delegate();
    if (environment->ExceptionCheck()) return default_value;
    if (key == nullptr) return delegate();
    const char *key_chars = environment->GetStringUTFChars(key, nullptr);
    if (environment->ExceptionCheck() || key_chars == nullptr) {
        if (key_chars != nullptr) environment->ReleaseStringUTFChars(key, key_chars);
        if (environment->ExceptionCheck()) environment->ExceptionClear();
        return delegate();
    }

    const char *override = (active_profile != nullptr) ? find_property_override(*active_profile, key_chars) : nullptr;
    environment->ReleaseStringUTFChars(key, key_chars);
    if (override == nullptr) return delegate();

    jstring result = environment->NewStringUTF(override);
    if (environment->ExceptionCheck() || result == nullptr) {
        if (environment->ExceptionCheck()) environment->ExceptionClear();
        return delegate();
    }
    return result;
}

}  // namespace s26spoof
