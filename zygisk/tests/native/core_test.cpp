#include <cstddef>
#include <cstdio>
#include <cstring>
#include <iterator>

#include "s26spoof/core.hpp"
#include "s26spoof/profile.hpp"
#include "test_support.hpp"

namespace {

using s26spoof::copy_property_value;
using s26spoof::find_property_override;
using s26spoof::is_target_process;
using s26spoof::DeviceProfile;

void target_process_matching_catches_prefix_leaks() {
    EXPECT_TRUE(is_target_process("com.ruanmei.ithome"));
    EXPECT_TRUE(is_target_process("com.ruanmei.ithome:web"));
    EXPECT_TRUE(is_target_process("com.ruanmei.ithome:push:worker"));

    EXPECT_FALSE(is_target_process(nullptr));
    EXPECT_FALSE(is_target_process(""));
    EXPECT_FALSE(is_target_process("com.ruanmei.ithome:"));
    EXPECT_FALSE(is_target_process("com.ruanmei.ithome2"));
    EXPECT_FALSE(is_target_process("com.ruanmei.ithome.web"));
    EXPECT_FALSE(is_target_process("system_server"));
}

void all_approved_product_aliases_return_profile_identity() {
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };

    constexpr const char *prefixes[] = {
        "ro.product.",
        "ro.product.system.",
        "ro.product.vendor.",
        "ro.product.product.",
        "ro.product.odm.",
        "ro.product.system_ext.",
    };
    struct ExpectedProperty {
        const char *suffix;
        const char *value;
    };
    constexpr ExpectedProperty properties[] = {
        {"manufacturer", "samsung"},
        {"brand", "samsung"},
        {"model", "SM-S9480"},
        {"device", "s26ultra"},
        {"name", "s26ultrachn"},
    };

    char key[96] = {};
    for (const char *prefix : prefixes) {
        for (const ExpectedProperty &property : properties) {
            const int length = std::snprintf(key, sizeof(key), "%s%s", prefix,
                                             property.suffix);
            EXPECT_TRUE(length > 0);
            EXPECT_TRUE(static_cast<std::size_t>(length) < sizeof(key));
            EXPECT_STREQ(find_property_override(profile, key), property.value);
        }
    }
}

void dynamic_profile_custom_values_are_respected() {
    const DeviceProfile pixel_profile = {
        "Google",
        "google",
        "Pixel 9 Pro",
        "komodo",
        "komodo_beta",
    };

    EXPECT_STREQ(find_property_override(pixel_profile, "ro.product.manufacturer"), "Google");
    EXPECT_STREQ(find_property_override(pixel_profile, "ro.product.system.brand"), "google");
    EXPECT_STREQ(find_property_override(pixel_profile, "ro.product.vendor.model"), "Pixel 9 Pro");
    EXPECT_STREQ(find_property_override(pixel_profile, "ro.product.odm.device"), "komodo");
    EXPECT_STREQ(find_property_override(pixel_profile, "ro.product.system_ext.name"), "komodo_beta");
}

void empty_profile_fields_return_null() {
    DeviceProfile sparse_profile = {};
    std::strncpy(sparse_profile.model, "SM-S9480", sizeof(sparse_profile.model) - 1);

    EXPECT_STREQ(find_property_override(sparse_profile, "ro.product.model"), "SM-S9480");
    EXPECT_NULL(find_property_override(sparse_profile, "ro.product.manufacturer"));
    EXPECT_NULL(find_property_override(sparse_profile, "ro.product.brand"));
    EXPECT_NULL(find_property_override(sparse_profile, "ro.product.device"));
    EXPECT_NULL(find_property_override(sparse_profile, "ro.product.name"));
}

void unrelated_properties_never_receive_an_override() {
    const DeviceProfile profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };

    EXPECT_NULL(find_property_override(profile, nullptr));
    EXPECT_NULL(find_property_override(profile, ""));
    EXPECT_NULL(find_property_override(profile, "ro.product.model.extra"));
    EXPECT_NULL(find_property_override(profile, "ro.product.vendor"));
    EXPECT_NULL(find_property_override(profile, "ro.product.vendor."));
    EXPECT_NULL(find_property_override(profile, "ro.build.version.release"));
    EXPECT_NULL(find_property_override(profile, "ro.build.version.sdk"));
    EXPECT_NULL(find_property_override(profile, "ro.build.fingerprint"));
    EXPECT_NULL(find_property_override(profile, "ro.serialno"));
    EXPECT_NULL(find_property_override(profile, "ro.hardware"));
}

void xiaomi_hyperos_properties_are_generated_and_huawei_masked() {
    const DeviceProfile xiaomi_profile = {
        "Xiaomi",
        "Xiaomi",
        "25019PNF3C",
        "xuanyuan",
        "xuanyuan",
    };

    // Xiaomi HyperOS properties should be active
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.mi.os.flavor"), "phone");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.mi.os.version.code"), "2");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.mi.os.version.name"), "OS2.0");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.mi.os.version.incremental"), "OS2.0.8.0.ULJCNXM");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.mi.os.version.publish"), "true");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.miui.ui.version.name"), "V816");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.miui.ui.version.code"), "2");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.miui.cust_variant"), "cn");

    // Huawei properties should be masked to empty string
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.build.version.emui"), "");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "ro.huawei.build.display.id"), "");
    EXPECT_STREQ(find_property_override(xiaomi_profile, "hw_sc.build.platform.version"), "");
}

void huawei_harmonyos_properties_are_generated_and_xiaomi_masked() {
    const DeviceProfile huawei_profile = {
        "HUAWEI",
        "HUAWEI",
        "ALN-AL00",
        "Allen",
        "allen",
    };

    // Huawei HarmonyOS properties should be active
    EXPECT_STREQ(find_property_override(huawei_profile, "ro.build.version.emui"), "EmotionUI_14.0.0");
    EXPECT_STREQ(find_property_override(huawei_profile, "ro.huawei.build.display.id"), "HarmonyOS 4.2.0");
    EXPECT_STREQ(find_property_override(huawei_profile, "ro.build.hw_emui_api_level"), "31");
    EXPECT_STREQ(find_property_override(huawei_profile, "hw_sc.build.platform.version"), "HarmonyOS 4.2.0");

    // Xiaomi properties should be masked to empty string
    EXPECT_STREQ(find_property_override(huawei_profile, "ro.mi.os.version.code"), "");
    EXPECT_STREQ(find_property_override(huawei_profile, "ro.miui.ui.version.name"), "");
    EXPECT_STREQ(find_property_override(huawei_profile, "ro.xiaomi.series"), "");
}

void samsung_masks_both_xiaomi_and_huawei_properties() {
    const DeviceProfile samsung_profile = {
        "samsung",
        "samsung",
        "SM-S9480",
        "s26ultra",
        "s26ultrachn",
    };

    // Both Xiaomi and Huawei properties should be masked to empty string
    EXPECT_STREQ(find_property_override(samsung_profile, "ro.mi.os.version.code"), "");
    EXPECT_STREQ(find_property_override(samsung_profile, "ro.mi.os.version.name"), "");
    EXPECT_STREQ(find_property_override(samsung_profile, "ro.miui.ui.version.name"), "");
    EXPECT_STREQ(find_property_override(samsung_profile, "ro.build.version.emui"), "");
    EXPECT_STREQ(find_property_override(samsung_profile, "ro.huawei.build.display.id"), "");
    EXPECT_STREQ(find_property_override(samsung_profile, "hw_sc.build.platform.version"), "");

    // Unrelated system properties remain unhandled (nullptr)
    EXPECT_NULL(find_property_override(samsung_profile, "ro.build.version.sdk"));
    EXPECT_NULL(find_property_override(samsung_profile, "ro.serialno"));
}

void bounded_copy_catches_overflow_and_termination_bugs() {
    char output[92] = {};
    EXPECT_EQ(copy_property_value("SM-S9480", output, sizeof(output)), 8);
    EXPECT_STREQ(output, "SM-S9480");

    std::memset(output, 'x', sizeof(output));
    EXPECT_EQ(copy_property_value("abcd", output, 3), 2);
    EXPECT_STREQ(output, "ab");

    output[0] = 'x';
    EXPECT_EQ(copy_property_value("", output, sizeof(output)), 0);
    EXPECT_STREQ(output, "");

    EXPECT_EQ(copy_property_value(nullptr, output, sizeof(output)), -1);
    EXPECT_EQ(copy_property_value("value", nullptr, sizeof(output)), -1);
    EXPECT_EQ(copy_property_value("value", output, 0), -1);
}

}  // namespace

int main() {
    target_process_matching_catches_prefix_leaks();
    all_approved_product_aliases_return_profile_identity();
    dynamic_profile_custom_values_are_respected();
    empty_profile_fields_return_null();
    unrelated_properties_never_receive_an_override();
    xiaomi_hyperos_properties_are_generated_and_huawei_masked();
    huawei_harmonyos_properties_are_generated_and_xiaomi_masked();
    samsung_masks_both_xiaomi_and_huawei_properties();
    bounded_copy_catches_overflow_and_termination_bugs();
    return test_support::finish("core_test");
}
