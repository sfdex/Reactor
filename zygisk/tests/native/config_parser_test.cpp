#include <cstddef>
#include <cstdio>
#include <cstring>
#include <string>
#include <unordered_map>

#include "s26spoof/config_parser.hpp"
#include "s26spoof/profile.hpp"
#include "test_support.hpp"

namespace {

using s26spoof::AppConfigEntry;
using s26spoof::DeviceProfile;
using s26spoof::parse_config_json;

void valid_full_config_parses_successfully() {
    const char *json = R"({
        "version": 1,
        "packages": {
            "com.ruanmei.ithome": {
                "enabled": true,
                "name": "Galaxy S26 Ultra",
                "manufacturer": "samsung",
                "brand": "samsung",
                "model": "SM-S9480",
                "device": "s26ultra",
                "product": "s26ultrachn"
            },
            "com.coolapk.market": {
                "enabled": false,
                "name": "Xiaomi 15 Pro",
                "manufacturer": "Xiaomi",
                "brand": "Xiaomi",
                "model": "24101PNB7C",
                "device": "haotian",
                "product": "haotian"
            }
        }
    })";

    std::unordered_map<std::string, AppConfigEntry> packages;
    EXPECT_TRUE(parse_config_json(json, &packages));
    EXPECT_EQ(packages.size(), static_cast<std::size_t>(2));

    auto it1 = packages.find("com.ruanmei.ithome");
    EXPECT_TRUE(it1 != packages.end());
    if (it1 != packages.end()) {
        EXPECT_TRUE(it1->second.enabled);
        EXPECT_STREQ(it1->second.name, "Galaxy S26 Ultra");
        EXPECT_STREQ(it1->second.profile.manufacturer, "samsung");
        EXPECT_STREQ(it1->second.profile.brand, "samsung");
        EXPECT_STREQ(it1->second.profile.model, "SM-S9480");
        EXPECT_STREQ(it1->second.profile.device, "s26ultra");
        EXPECT_STREQ(it1->second.profile.product, "s26ultrachn");
    }

    auto it2 = packages.find("com.coolapk.market");
    EXPECT_TRUE(it2 != packages.end());
    if (it2 != packages.end()) {
        EXPECT_FALSE(it2->second.enabled);
        EXPECT_STREQ(it2->second.name, "Xiaomi 15 Pro");
        EXPECT_STREQ(it2->second.profile.manufacturer, "Xiaomi");
        EXPECT_STREQ(it2->second.profile.brand, "Xiaomi");
        EXPECT_STREQ(it2->second.profile.model, "24101PNB7C");
        EXPECT_STREQ(it2->second.profile.device, "haotian");
        EXPECT_STREQ(it2->second.profile.product, "haotian");
    }
}

void empty_and_minimal_packages_parses() {
    const char *empty_pkg_json = R"({
        "version": 1,
        "packages": {}
    })";
    std::unordered_map<std::string, AppConfigEntry> packages;
    EXPECT_TRUE(parse_config_json(empty_pkg_json, &packages));
    EXPECT_EQ(packages.size(), static_cast<std::size_t>(0));

    const char *minimal_json = R"({
        "version": 1,
        "extra_root_field": "ignored",
        "packages": {
            "com.test.app": {
                "enabled": true,
                "model": "OnlyModelSet",
                "extra_field": 42,
                "nested_ignored": {
                    "foo": ["bar", 1, 2, true]
                }
            }
        }
    })";
    EXPECT_TRUE(parse_config_json(minimal_json, &packages));
    EXPECT_EQ(packages.size(), static_cast<std::size_t>(1));
    auto it = packages.find("com.test.app");
    EXPECT_TRUE(it != packages.end());
    if (it != packages.end()) {
        EXPECT_TRUE(it->second.enabled);
        EXPECT_STREQ(it->second.name, "");
        EXPECT_STREQ(it->second.profile.manufacturer, "");
        EXPECT_STREQ(it->second.profile.brand, "");
        EXPECT_STREQ(it->second.profile.model, "OnlyModelSet");
        EXPECT_STREQ(it->second.profile.device, "");
        EXPECT_STREQ(it->second.profile.product, "");
    }
}

void string_escapes_handled() {
    const char *json = R"({
        "version": 1,
        "packages": {
            "com.escape.test": {
                "enabled": true,
                "name": "Name with \"quotes\" and \\backslash",
                "model": "Model\/Slash\ttab\nnewline",
                "product": "\u0053\u0032\u0036 \u4e2d\u6587"
            }
        }
    })";

    std::unordered_map<std::string, AppConfigEntry> packages;
    EXPECT_TRUE(parse_config_json(json, &packages));
    auto it = packages.find("com.escape.test");
    EXPECT_TRUE(it != packages.end());
    if (it != packages.end()) {
        EXPECT_STREQ(it->second.name, "Name with \"quotes\" and \\backslash");
        EXPECT_STREQ(it->second.profile.model, "Model/Slash\ttab\nnewline");
        EXPECT_STREQ(it->second.profile.product, "S26 中文");
    }
}

void long_strings_are_safely_bounded() {
    std::string long_val(128, 'A');
    std::string json = "{\"version\":1,\"packages\":{\"com.test\":{\"enabled\":true,\"model\":\"" +
                       long_val + "\"}}}";

    std::unordered_map<std::string, AppConfigEntry> packages;
    EXPECT_TRUE(parse_config_json(json.c_str(), &packages));
    auto it = packages.find("com.test");
    EXPECT_TRUE(it != packages.end());
    if (it != packages.end()) {
        EXPECT_EQ(std::strlen(it->second.profile.model), static_cast<std::size_t>(63));
        EXPECT_EQ(it->second.profile.model[63], '\0');
    }
}

void malformed_inputs_fail_gracefully() {
    std::unordered_map<std::string, AppConfigEntry> packages;

    EXPECT_FALSE(parse_config_json(nullptr, &packages));
    EXPECT_FALSE(parse_config_json("{}", nullptr));
    EXPECT_FALSE(parse_config_json("", &packages));
    EXPECT_FALSE(parse_config_json("   ", &packages));
    EXPECT_FALSE(parse_config_json("not a json", &packages));
    EXPECT_FALSE(parse_config_json("{", &packages));
    EXPECT_FALSE(parse_config_json("{\"packages\":", &packages));
    EXPECT_FALSE(parse_config_json("{\"packages\": [1, 2, 3]}", &packages));
    EXPECT_FALSE(parse_config_json("{\"packages\": {\"com.test\": 123}}", &packages));
    EXPECT_FALSE(parse_config_json("{\"packages\": {\"com.test\": {\"enabled\": \"true\"}}}", &packages));
    EXPECT_FALSE(parse_config_json("{\"packages\": {\"com.test\": {\"model\": 12345}}}", &packages));
    EXPECT_FALSE(parse_config_json("{\"packages\": {\"com.test\": {\"model\": \"unclosed}}}", &packages));
    EXPECT_FALSE(parse_config_json("{\"version\": \"not_a_number\", \"packages\": {}}", &packages));
    EXPECT_FALSE(parse_config_json("{\"version\": 1, \"packages\": {}} trailing_garbage", &packages));
    EXPECT_FALSE(parse_config_json("{\"version\": 1, \"packages\": {\"pkg\": {\"model\": \"\\u12\"}}}", &packages));
}

void reparsing_clears_previous_entries() {
    std::unordered_map<std::string, AppConfigEntry> packages;
    const char *json1 = "{\"version\":1,\"packages\":{\"com.first\":{\"enabled\":true,\"model\":\"M1\"}}}";
    const char *json2 = "{\"version\":1,\"packages\":{\"com.second\":{\"enabled\":false,\"model\":\"M2\"}}}";

    EXPECT_TRUE(parse_config_json(json1, &packages));
    EXPECT_EQ(packages.size(), static_cast<std::size_t>(1));
    EXPECT_TRUE(packages.count("com.first") == 1);

    EXPECT_TRUE(parse_config_json(json2, &packages));
    EXPECT_EQ(packages.size(), static_cast<std::size_t>(1));
    EXPECT_TRUE(packages.count("com.first") == 0);
    EXPECT_TRUE(packages.count("com.second") == 1);
}

}  // namespace

int main() {
    valid_full_config_parses_successfully();
    empty_and_minimal_packages_parses();
    string_escapes_handled();
    long_strings_are_safely_bounded();
    malformed_inputs_fail_gracefully();
    reparsing_clears_previous_entries();
    return test_support::finish("config_parser_test");
}
