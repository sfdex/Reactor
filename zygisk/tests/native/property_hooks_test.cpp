#include <cstdint>
#include <cstring>

#include "s26spoof/profile.hpp"
#include "s26spoof/property_hooks.hpp"
#include "test_support.hpp"

struct prop_info {};

namespace {

using s26spoof::DeviceProfile;
using s26spoof::get_active_profile;
using s26spoof::hooked_property_get;
using s26spoof::hooked_property_read_callback;
using s26spoof::set_active_profile;
using s26spoof::set_original_property_get;
using s26spoof::set_original_property_read_callback;

const char *callback_name = nullptr;
const char *callback_value = nullptr;
std::uint32_t callback_serial = 0;

int fake_property_get(const char *, char *value) {
    std::strcpy(value, "15.0");
    return 4;
}

void fake_read_product_brand(const prop_info *,
                             s26spoof::PropertyReadCallback callback,
                             void *cookie) {
    callback(cookie, "ro.product.brand", "realbrand", 73);
}

void fake_read_android_version(const prop_info *,
                               s26spoof::PropertyReadCallback callback,
                               void *cookie) {
    callback(cookie, "ro.build.version.release", "15", 91);
}

void capture_callback(void *, const char *name, const char *value,
                      std::uint32_t serial) {
    callback_name = name;
    callback_value = value;
    callback_serial = serial;
}

void property_get_overrides_only_when_active_profile_matches() {
    set_original_property_get(fake_property_get);

    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);
    EXPECT_TRUE(get_active_profile() == &profile);

    char value[92] = {};
    EXPECT_EQ(hooked_property_get("ro.product.model", value), 8);
    EXPECT_STREQ(value, "SM-S9480");

    std::memset(value, 0, sizeof(value));
    EXPECT_EQ(hooked_property_get("ro.build.version.release", value), 4);
    EXPECT_STREQ(value, "15.0");
}

void property_get_supports_dynamic_switching() {
    set_original_property_get(fake_property_get);

    const DeviceProfile pixel = {
        "Google",
        "google",
        "Pixel 9",
        "tokay",
        "tokay_beta",
    };
    set_active_profile(&pixel);

    char value[92] = {};
    EXPECT_EQ(hooked_property_get("ro.product.model", value), 7);
    EXPECT_STREQ(value, "Pixel 9");
}

void property_get_delegates_when_active_profile_is_null() {
    set_original_property_get(fake_property_get);
    set_active_profile(nullptr);
    EXPECT_TRUE(get_active_profile() == nullptr);

    char value[92] = {};
    EXPECT_EQ(hooked_property_get("ro.product.model", value), 4);
    EXPECT_STREQ(value, "15.0");
}

void uninstalled_wrapper_handles_a_missing_original_safely() {
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);
    set_original_property_get(nullptr);

    char value[92] = {'x', '\0'};
    EXPECT_EQ(hooked_property_get("ro.build.version.release", value), 0);
    EXPECT_STREQ(value, "");
    EXPECT_EQ(hooked_property_get("ro.product.model", nullptr), 0);
}

void read_callback_substitutes_active_profile_value_and_preserves_metadata() {
    set_original_property_read_callback(fake_read_product_brand);
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);

    callback_name = nullptr;
    callback_value = nullptr;
    callback_serial = 0;

    prop_info info{};
    hooked_property_read_callback(&info, capture_callback, nullptr);

    EXPECT_STREQ(callback_name, "ro.product.brand");
    EXPECT_STREQ(callback_value, "samsung");
    EXPECT_EQ(callback_serial, static_cast<std::uint32_t>(73));
}

void read_callback_preserves_unrelated_values() {
    set_original_property_read_callback(fake_read_android_version);
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };
    set_active_profile(&profile);

    callback_name = nullptr;
    callback_value = nullptr;
    callback_serial = 0;

    prop_info info{};
    hooked_property_read_callback(&info, capture_callback, nullptr);

    EXPECT_STREQ(callback_name, "ro.build.version.release");
    EXPECT_STREQ(callback_value, "15");
    EXPECT_EQ(callback_serial, static_cast<std::uint32_t>(91));
}

void read_callback_delegates_unmodified_when_no_active_profile() {
    set_original_property_read_callback(fake_read_product_brand);
    set_active_profile(nullptr);

    callback_name = nullptr;
    callback_value = nullptr;
    callback_serial = 0;

    prop_info info{};
    hooked_property_read_callback(&info, capture_callback, nullptr);

    EXPECT_STREQ(callback_name, "ro.product.brand");
    EXPECT_STREQ(callback_value, "realbrand");
    EXPECT_EQ(callback_serial, static_cast<std::uint32_t>(73));
}

void property_get_handles_hyperos_and_harmonyos_overrides_and_masking() {
    set_original_property_get(fake_property_get);

    const DeviceProfile xiaomi_profile = {
        "Xiaomi",
        "Xiaomi",
        "25019PNF3C",
        "xuanyuan",
        "xuanyuan",
    };
    set_active_profile(&xiaomi_profile);

    char value[92] = {};
    EXPECT_EQ(hooked_property_get("ro.mi.os.version.name", value), 5);
    EXPECT_STREQ(value, "OS2.0");

    std::memset(value, 'x', sizeof(value));
    EXPECT_EQ(hooked_property_get("ro.huawei.build.display.id", value), 0);
    EXPECT_STREQ(value, "");

    const DeviceProfile huawei_profile = {
        "HUAWEI",
        "HUAWEI",
        "ALN-AL00",
        "Allen",
        "allen",
    };
    set_active_profile(&huawei_profile);

    std::memset(value, 0, sizeof(value));
    EXPECT_EQ(hooked_property_get("ro.huawei.build.display.id", value), 15);
    EXPECT_STREQ(value, "HarmonyOS 4.2.0");

    std::memset(value, 'x', sizeof(value));
    EXPECT_EQ(hooked_property_get("ro.mi.os.version.code", value), 0);
    EXPECT_STREQ(value, "");
}

void read_callback_handles_missing_functions() {
    prop_info info{};
    set_original_property_read_callback(nullptr);
    hooked_property_read_callback(&info, capture_callback, nullptr);
    set_original_property_read_callback(fake_read_product_brand);
    hooked_property_read_callback(&info, nullptr, nullptr);
    EXPECT_TRUE(true);
}

}  // namespace

int main() {
    property_get_overrides_only_when_active_profile_matches();
    property_get_supports_dynamic_switching();
    property_get_delegates_when_active_profile_is_null();
    uninstalled_wrapper_handles_a_missing_original_safely();
    read_callback_substitutes_active_profile_value_and_preserves_metadata();
    read_callback_preserves_unrelated_values();
    read_callback_delegates_unmodified_when_no_active_profile();
    property_get_handles_hyperos_and_harmonyos_overrides_and_masking();
    read_callback_handles_missing_functions();
    return test_support::finish("property_hooks_test");
}
