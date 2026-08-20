#include <cstdint>
#include <cstring>

#include "s26spoof/native_hooks.hpp"
#include "s26spoof/property_hooks.hpp"
#include "test_support.hpp"

struct prop_info {};

namespace {

struct FakeHookApi {
    bool commit_result = true;
    int registrations = 0;
    int commits = 0;
    bool publish_get_original = true;
    bool publish_read_original = true;
};

int fake_property_get(const char *, char *value) {
    std::strcpy(value, "real-value");
    return 10;
}

void fake_property_read(const prop_info *, s26spoof::PropertyReadCallback callback,
                        void *cookie) {
    callback(cookie, "ro.build.version.release", "15", 42);
}

void *resolve_all(const char *symbol) {
    if (std::strcmp(symbol, "__system_property_get") == 0) {
        return reinterpret_cast<void *>(fake_property_get);
    }
    if (std::strcmp(symbol, "__system_property_read_callback") == 0) {
        return reinterpret_cast<void *>(fake_property_read);
    }
    return nullptr;
}

void *resolve_only_get(const char *symbol) {
    return std::strcmp(symbol, "__system_property_get") == 0
               ? reinterpret_cast<void *>(fake_property_get)
               : nullptr;
}

void register_hook(void *context, dev_t, ino_t, const char *symbol, void *,
                   void **original) {
    auto *fake = static_cast<FakeHookApi *>(context);
    ++fake->registrations;
    if (std::strcmp(symbol, "__system_property_get") == 0) {
        if (fake->publish_get_original) {
            *original = reinterpret_cast<void *>(fake_property_get);
        }
    } else if (std::strcmp(symbol, "__system_property_read_callback") == 0) {
        if (fake->publish_read_original) {
            *original = reinterpret_cast<void *>(fake_property_read);
        }
    }
}

bool commit_hooks(void *context) {
    auto *fake = static_cast<FakeHookApi *>(context);
    ++fake->commits;
    return fake->commit_result;
}

void capture(void *cookie, const char *, const char *value, std::uint32_t serial) {
    auto **captured = static_cast<const char **>(cookie);
    *captured = value;
    EXPECT_EQ(serial, static_cast<std::uint32_t>(42));
}

s26spoof::MapIdentity executable_map() {
    s26spoof::MapIdentity map{};
    map.device = static_cast<dev_t>(1);
    map.inode = static_cast<ino_t>(2);
    map.executable = true;
    return map;
}

s26spoof::NativeHookApi make_api(FakeHookApi *fake) {
    return {fake, register_hook, commit_hooks};
}

void missing_fallback_refuses_all_hook_registration() {
    FakeHookApi fake;
    const s26spoof::MapIdentity map = executable_map();
    const s26spoof::NativeHookStatus status =
        s26spoof::install_native_property_hooks(make_api(&fake), &map, 1,
                                                resolve_only_get);

    EXPECT_FALSE(status.fallbacks_ready);
    EXPECT_FALSE(status.commit_succeeded);
    EXPECT_FALSE(status.property_get);
    EXPECT_FALSE(status.property_read_callback);
    EXPECT_EQ(fake.registrations, 0);
    EXPECT_EQ(fake.commits, 0);
}

void commit_failure_keeps_prepublished_fail_open_fallbacks() {
    FakeHookApi fake;
    fake.commit_result = false;
    const s26spoof::MapIdentity map = executable_map();
    const s26spoof::NativeHookStatus status =
        s26spoof::install_native_property_hooks(make_api(&fake), &map, 1,
                                                resolve_all);

    EXPECT_TRUE(status.fallbacks_ready);
    EXPECT_FALSE(status.commit_succeeded);
    EXPECT_FALSE(status.property_get);
    EXPECT_FALSE(status.property_read_callback);
    EXPECT_EQ(fake.registrations, 2);
    EXPECT_EQ(fake.commits, 1);

    char value[92] = {};
    EXPECT_EQ(s26spoof::hooked_property_get("ro.build.version.release", value), 10);
    EXPECT_STREQ(value, "real-value");

    const char *captured = nullptr;
    prop_info info{};
    s26spoof::hooked_property_read_callback(&info, capture, &captured);
    EXPECT_STREQ(captured, "15");
}

void successful_commit_reports_each_hook_independently() {
    FakeHookApi fake;
    const s26spoof::MapIdentity map = executable_map();
    const s26spoof::NativeHookStatus status =
        s26spoof::install_native_property_hooks(make_api(&fake), &map, 1,
                                                resolve_all);

    EXPECT_TRUE(status.fallbacks_ready);
    EXPECT_TRUE(status.commit_succeeded);
    EXPECT_TRUE(status.property_get);
    EXPECT_TRUE(status.property_read_callback);
}

void partial_commit_result_is_not_reported_as_full_coverage() {
    FakeHookApi fake;
    fake.publish_read_original = false;
    const s26spoof::MapIdentity map = executable_map();
    const s26spoof::NativeHookStatus status =
        s26spoof::install_native_property_hooks(make_api(&fake), &map, 1,
                                                resolve_all);

    EXPECT_TRUE(status.commit_succeeded);
    EXPECT_TRUE(status.property_get);
    EXPECT_FALSE(status.property_read_callback);
}

}  // namespace

int main() {
    missing_fallback_refuses_all_hook_registration();
    commit_failure_keeps_prepublished_fail_open_fallbacks();
    successful_commit_reports_each_hook_independently();
    partial_commit_result_is_not_reported_as_full_coverage();
    return test_support::finish("native_hooks_test");
}
