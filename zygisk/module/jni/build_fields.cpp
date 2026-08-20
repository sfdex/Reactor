#include "s26spoof/build_fields.hpp"

namespace s26spoof {
namespace {

bool clear_pending_exception(JNIEnv *environment) noexcept {
    if (!environment->ExceptionCheck()) return false;
    environment->ExceptionClear();
    return true;
}

struct FieldEntry {
    const char *name;
    const char *value;
};

}  // namespace

BuildWriteResult write_build_fields(JNIEnv *environment,
                                    const DeviceProfile &profile) noexcept {
    BuildWriteResult result{};
    if (environment == nullptr) return result;
    if (environment->ExceptionCheck()) return result;

    const FieldEntry fields[] = {
        {"MANUFACTURER", profile.manufacturer},
        {"BRAND", profile.brand},
        {"MODEL", profile.model},
        {"DEVICE", profile.device},
        {"PRODUCT", profile.product},
    };

    jclass build_class = environment->FindClass("android/os/Build");
    if (clear_pending_exception(environment) || build_class == nullptr) {
        result.exception_cleared = true;
        return result;
    }

    for (const FieldEntry &field : fields) {
        if (field.value == nullptr || field.value[0] == '\0') {
            continue;
        }
        ++result.attempted;
        jfieldID field_id = environment->GetStaticFieldID(
            build_class, field.name, "Ljava/lang/String;");
        if (clear_pending_exception(environment) || field_id == nullptr) {
            result.exception_cleared = true;
            continue;
        }

        jstring value = environment->NewStringUTF(field.value);
        if (clear_pending_exception(environment) || value == nullptr) {
            result.exception_cleared = true;
            continue;
        }

        environment->SetStaticObjectField(build_class, field_id, value);
        const bool write_failed = clear_pending_exception(environment);
        environment->DeleteLocalRef(value);
        if (write_failed) {
            result.exception_cleared = true;
            continue;
        }
        ++result.succeeded;
    }

    environment->DeleteLocalRef(build_class);
    return result;
}

}  // namespace s26spoof
