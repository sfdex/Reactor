#include "s26spoof/core.hpp"

#include <string.h>

#include "s26spoof/identity.hpp"

namespace s26spoof {
namespace {

constexpr const char *kPropertyPrefixes[] = {
    "ro.product.",
    "ro.product.system.",
    "ro.product.vendor.",
    "ro.product.product.",
    "ro.product.odm.",
    "ro.product.system_ext.",
};

const char *find_suffix_value(const DeviceProfile &profile, const char *suffix) noexcept {
    const char *value = nullptr;
    if (strcmp(suffix, "manufacturer") == 0) {
        value = profile.manufacturer;
    } else if (strcmp(suffix, "brand") == 0) {
        value = profile.brand;
    } else if (strcmp(suffix, "model") == 0) {
        value = profile.model;
    } else if (strcmp(suffix, "device") == 0) {
        value = profile.device;
    } else if (strcmp(suffix, "name") == 0) {
        value = profile.product;
    }
    if (value != nullptr && value[0] != '\0') {
        return value;
    }
    return nullptr;
}

static bool contains_ignore_case(const char *haystack, const char *needle) noexcept {
    if (haystack == nullptr || needle == nullptr) return false;
    const size_t h_len = strlen(haystack);
    const size_t n_len = strlen(needle);
    if (n_len == 0) return true;
    if (h_len < n_len) return false;
    for (size_t i = 0; i <= h_len - n_len; ++i) {
        size_t j = 0;
        while (j < n_len) {
            char c1 = haystack[i + j];
            char c2 = needle[j];
            if (c1 >= 'A' && c1 <= 'Z') c1 += 32;
            if (c2 >= 'A' && c2 <= 'Z') c2 += 32;
            if (c1 != c2) break;
            ++j;
        }
        if (j == n_len) return true;
    }
    return false;
}

static bool is_xiaomi(const DeviceProfile &profile) noexcept {
    return contains_ignore_case(profile.brand, "xiaomi") ||
           contains_ignore_case(profile.brand, "redmi") ||
           contains_ignore_case(profile.manufacturer, "xiaomi");
}

static bool is_huawei(const DeviceProfile &profile) noexcept {
    return contains_ignore_case(profile.brand, "huawei") ||
           contains_ignore_case(profile.brand, "honor") ||
           contains_ignore_case(profile.manufacturer, "huawei") ||
           contains_ignore_case(profile.manufacturer, "honor");
}

}  // namespace

bool is_target_process(const char *name) noexcept {
    if (name == nullptr) return false;

    constexpr size_t package_length = sizeof(kPackageName) - 1;
    if (strcmp(name, kPackageName) == 0) return true;
    return strncmp(name, kPackageName, package_length) == 0 &&
           name[package_length] == ':' && name[package_length + 1] != '\0';
}

const char *find_property_override(const DeviceProfile &profile, const char *key) noexcept {
    if (key == nullptr || key[0] == '\0') return nullptr;

    // 1. Standard 5 core product properties
    for (const char *prefix : kPropertyPrefixes) {
        const size_t prefix_length = strlen(prefix);
        if (strncmp(key, prefix, prefix_length) != 0) continue;
        const char *value = find_suffix_value(profile, key + prefix_length);
        if (value != nullptr) return value;
    }

    const bool target_is_xiaomi = is_xiaomi(profile);
    const bool target_is_huawei = is_huawei(profile);

    // 2. Xiaomi HyperOS 2 / MIUI Properties & Anti-leak masking
    if (target_is_xiaomi) {
        if (strcmp(key, "ro.mi.os.flavor") == 0) return "phone";
        if (strcmp(key, "ro.mi.os.version.code") == 0) return "2";
        if (strcmp(key, "ro.mi.os.version.name") == 0) return "OS2.0";
        if (strcmp(key, "ro.mi.os.version.incremental") == 0) return "OS2.0.8.0.ULJCNXM";
        if (strcmp(key, "ro.mi.os.version.publish") == 0) return "true";
        if (strcmp(key, "ro.miui.ui.version.name") == 0) return "V816";
        if (strcmp(key, "ro.miui.ui.version.code") == 0) return "2";
        if (strcmp(key, "ro.miui.version.code_time") == 0) return "1720000000";
        if (strcmp(key, "ro.miui.cust_variant") == 0) return "cn";
        if (strcmp(key, "ro.miui.region") == 0) return "CN";
    } else {
        if (strncmp(key, "ro.mi.os.", 9) == 0 ||
            strncmp(key, "ro.miui.", 8) == 0 ||
            strncmp(key, "ro.xiaomi.", 10) == 0) {
            return "";
        }
    }

    // 3. Huawei HarmonyOS / EMUI Properties & Anti-leak masking
    if (target_is_huawei) {
        if (strcmp(key, "ro.build.version.emui") == 0) return "EmotionUI_14.0.0";
        if (strcmp(key, "ro.huawei.build.display.id") == 0) return "HarmonyOS 4.2.0";
        if (strcmp(key, "ro.build.hw_emui_api_level") == 0) return "31";
        if (strcmp(key, "hw_sc.build.platform.version") == 0) return "HarmonyOS 4.2.0";
        if (strcmp(key, "hw_sc.build.os.enable") == 0) return "true";
    } else {
        if (strncmp(key, "ro.huawei.", 10) == 0 ||
            strncmp(key, "hw_sc.", 6) == 0 ||
            strcmp(key, "ro.build.version.emui") == 0 ||
            strncmp(key, "ro.build.hw_emui_", 16) == 0) {
            return "";
        }
    }

    return nullptr;
}

int copy_property_value(const char *value, char *destination,
                        size_t capacity) noexcept {
    if (value == nullptr || destination == nullptr || capacity == 0) return -1;

    const size_t value_length = strlen(value);
    const size_t copy_length =
        value_length < capacity - 1 ? value_length : capacity - 1;
    if (copy_length != 0) memcpy(destination, value, copy_length);
    destination[copy_length] = '\0';
    return static_cast<int>(copy_length);
}

}  // namespace s26spoof
