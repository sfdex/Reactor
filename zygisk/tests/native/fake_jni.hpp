#pragma once

#include <jni.h>
#include <type_traits>

#include <cstring>

class FakeJni {
public:
    FakeJni() {
        active_ = this;
        interface_.FindClass = find_class;
        interface_.GetStaticFieldID = get_static_field_id;
        interface_.NewStringUTF = new_string_utf;
        interface_.SetStaticObjectField = set_static_object_field;
        interface_.GetStringUTFChars = get_string_utf_chars;
        interface_.ReleaseStringUTFChars = release_string_utf_chars;
        interface_.ExceptionCheck = exception_check;
        interface_.ExceptionClear = exception_clear;
        interface_.DeleteLocalRef = delete_local_ref;
        environment_.functions = &interface_;
    }

    ~FakeJni() { active_ = nullptr; }

    JNIEnv *environment() { return &environment_; }

    jstring string(const char *value) const {
        return reinterpret_cast<jstring>(const_cast<char *>(value));
    }

    const char *string_value(jstring value) const {
        return reinterpret_cast<const char *>(value);
    }

    const char *value_for(const char *field_name) const {
        for (const Field &field : fields_) {
            if (std::strcmp(field.name, field_name) == 0) return field.value;
        }
        return nullptr;
    }

    void fail_field(const char *field_name) { failed_field_ = field_name; }
    void fail_utf_access() { fail_utf_access_ = true; }
    void seed_exception() { exception_pending_ = true; }
    bool has_exception() const { return exception_pending_; }
    int cleared_exception_count() const { return cleared_exception_count_; }

private:
    struct Field {
        const char *name;
        const char *value;
    };

    static FakeJni &active() { return *active_; }

    static jclass JNICALL find_class(JNIEnv *, const char *name) {
        if (name != nullptr && std::strcmp(name, "android/os/Build") == 0) {
            return reinterpret_cast<jclass>(&active());
        }
        active().exception_pending_ = true;
        return nullptr;
    }

    static jfieldID JNICALL get_static_field_id(JNIEnv *, jclass, const char *name,
                                                 const char *signature) {
        FakeJni &fake = active();
        if (name == nullptr || signature == nullptr ||
            std::strcmp(signature, "Ljava/lang/String;") != 0 ||
            (fake.failed_field_ != nullptr &&
             std::strcmp(fake.failed_field_, name) == 0)) {
            fake.exception_pending_ = true;
            return nullptr;
        }
        for (Field &field : fake.fields_) {
            if (std::strcmp(field.name, name) == 0) {
                return reinterpret_cast<jfieldID>(&field);
            }
        }
        fake.exception_pending_ = true;
        return nullptr;
    }

    static jstring JNICALL new_string_utf(JNIEnv *, const char *value) {
        if (value == nullptr) {
            active().exception_pending_ = true;
            return nullptr;
        }
        return active().string(value);
    }

    static void JNICALL set_static_object_field(JNIEnv *, jclass, jfieldID field_id,
                                                jobject value) {
        if (field_id == nullptr || value == nullptr) {
            active().exception_pending_ = true;
            return;
        }
        auto *field = reinterpret_cast<Field *>(field_id);
        field->value = reinterpret_cast<const char *>(value);
    }

    static const char *JNICALL get_string_utf_chars(JNIEnv *, jstring value,
                                                     jboolean *) {
        FakeJni &fake = active();
        if (value == nullptr || fake.fail_utf_access_) {
            fake.exception_pending_ = true;
            return nullptr;
        }
        return fake.string_value(value);
    }

    static void JNICALL release_string_utf_chars(JNIEnv *, jstring, const char *) {}

    static jboolean JNICALL exception_check(JNIEnv *) {
        return active().exception_pending_ ? JNI_TRUE : JNI_FALSE;
    }

    static void JNICALL exception_clear(JNIEnv *) {
        FakeJni &fake = active();
        if (fake.exception_pending_) ++fake.cleared_exception_count_;
        fake.exception_pending_ = false;
    }

    static void JNICALL delete_local_ref(JNIEnv *, jobject) {}

    inline static FakeJni *active_ = nullptr;
    using NativeInterfaceType = std::remove_cv_t<std::remove_pointer_t<decltype(JNIEnv{}.functions)>>;
    NativeInterfaceType interface_{};
    JNIEnv environment_{};
    Field fields_[5] = {
        {"MANUFACTURER", nullptr},
        {"BRAND", nullptr},
        {"MODEL", nullptr},
        {"DEVICE", nullptr},
        {"PRODUCT", nullptr},
    };
    const char *failed_field_ = nullptr;
    bool fail_utf_access_ = false;
    bool exception_pending_ = false;
    int cleared_exception_count_ = 0;
};
