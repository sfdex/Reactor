#include "fake_jni.hpp"
#include "s26spoof/profile.hpp"
#include "s26spoof/property_hooks.hpp"
#include "test_support.hpp"

namespace {

using s26spoof::DeviceProfile;
using s26spoof::set_active_profile;

jstring fake_java_property_get(JNIEnv *, jclass, jstring, jstring default_value) {
    return default_value;
}

void recognized_java_property_returns_spoof() {
    FakeJni fake;
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);
    s26spoof::set_original_java_property_get(fake_java_property_get);
    const jstring result = s26spoof::hooked_java_property_get(
        fake.environment(), nullptr, fake.string("ro.product.model"),
        fake.string("real-model"));

    EXPECT_STREQ(fake.string_value(result), "SM-S9480");
    EXPECT_FALSE(fake.has_exception());
}

void dynamic_java_property_returns_custom_profile() {
    FakeJni fake;
    const DeviceProfile pixel = {
        "Google",
        "google",
        "Pixel 9 Pro",
        "komodo",
        "komodo_beta",
    };
    set_active_profile(&pixel);
    s26spoof::set_original_java_property_get(fake_java_property_get);
    const jstring result = s26spoof::hooked_java_property_get(
        fake.environment(), nullptr, fake.string("ro.product.vendor.model"),
        fake.string("real-model"));

    EXPECT_STREQ(fake.string_value(result), "Pixel 9 Pro");
    EXPECT_FALSE(fake.has_exception());
}

void null_profile_delegates_to_original() {
    FakeJni fake;
    set_active_profile(nullptr);
    s26spoof::set_original_java_property_get(fake_java_property_get);
    const jstring result = s26spoof::hooked_java_property_get(
        fake.environment(), nullptr, fake.string("ro.product.model"),
        fake.string("real-model"));

    EXPECT_STREQ(fake.string_value(result), "real-model");
    EXPECT_FALSE(fake.has_exception());
}

void unrelated_java_property_delegates_to_original() {
    FakeJni fake;
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);
    s26spoof::set_original_java_property_get(fake_java_property_get);
    const jstring result = s26spoof::hooked_java_property_get(
        fake.environment(), nullptr, fake.string("ro.build.version.release"),
        fake.string("15.0"));

    EXPECT_STREQ(fake.string_value(result), "15.0");
    EXPECT_FALSE(fake.has_exception());
}

void utf_failure_is_cleared_before_fallback() {
    FakeJni fake;
    fake.fail_utf_access();
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);
    s26spoof::set_original_java_property_get(fake_java_property_get);
    const jstring result = s26spoof::hooked_java_property_get(
        fake.environment(), nullptr, fake.string("ro.product.model"),
        fake.string("fallback"));

    EXPECT_STREQ(fake.string_value(result), "fallback");
    EXPECT_FALSE(fake.has_exception());
    EXPECT_TRUE(fake.cleared_exception_count() > 0);
}

void missing_original_returns_default_without_throwing() {
    FakeJni fake;
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);
    s26spoof::set_original_java_property_get(nullptr);
    const jstring result = s26spoof::hooked_java_property_get(
        fake.environment(), nullptr, fake.string("ro.build.version.release"),
        fake.string("default"));

    EXPECT_STREQ(fake.string_value(result), "default");
    EXPECT_FALSE(fake.has_exception());
}

void preexisting_exception_is_preserved_without_jni_work() {
    FakeJni fake;
    fake.seed_exception();
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);
    s26spoof::set_original_java_property_get(fake_java_property_get);
    const jstring fallback = fake.string("fallback");
    const jstring result = s26spoof::hooked_java_property_get(
        fake.environment(), nullptr, fake.string("ro.product.model"), fallback);

    EXPECT_TRUE(result == fallback);
    EXPECT_TRUE(fake.has_exception());
    EXPECT_EQ(fake.cleared_exception_count(), 0);
}

}  // namespace

int main() {
    recognized_java_property_returns_spoof();
    dynamic_java_property_returns_custom_profile();
    null_profile_delegates_to_original();
    unrelated_java_property_delegates_to_original();
    utf_failure_is_cleared_before_fallback();
    missing_original_returns_default_without_throwing();
    preexisting_exception_is_preserved_without_jni_work();
    return test_support::finish("java_property_hook_test");
}
