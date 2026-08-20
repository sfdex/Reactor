#include <cstddef>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <unordered_map>

#include "s26spoof/companion.hpp"
#include "s26spoof/profile.hpp"
#include "test_support.hpp"
#include "zygisk.hpp"

namespace {

using s26spoof::AppConfigEntry;
using s26spoof::DeviceProfile;
using s26spoof::match_process_profile;
using s26spoof::query_profile_from_socket;
using s26spoof::query_process_profile;
using s26spoof::companion_handler;
using s26spoof::set_config_path;
using s26spoof::get_config_path;
using s26spoof::reset_companion_cache;

std::unordered_map<std::string, AppConfigEntry> create_sample_packages() {
    std::unordered_map<std::string, AppConfigEntry> map;

    AppConfigEntry ithome{};
    ithome.enabled = true;
    std::snprintf(ithome.name, sizeof(ithome.name), "Galaxy S26 Ultra");
    std::snprintf(ithome.profile.manufacturer, sizeof(ithome.profile.manufacturer), "samsung");
    std::snprintf(ithome.profile.brand, sizeof(ithome.profile.brand), "samsung");
    std::snprintf(ithome.profile.model, sizeof(ithome.profile.model), "SM-S9480");
    std::snprintf(ithome.profile.device, sizeof(ithome.profile.device), "s26ultra");
    std::snprintf(ithome.profile.product, sizeof(ithome.profile.product), "s26ultrachn");
    map["com.ruanmei.ithome"] = ithome;

    AppConfigEntry coolapk{};
    coolapk.enabled = false;
    std::snprintf(coolapk.name, sizeof(coolapk.name), "Xiaomi 15 Pro");
    std::snprintf(coolapk.profile.manufacturer, sizeof(coolapk.profile.manufacturer), "Xiaomi");
    std::snprintf(coolapk.profile.brand, sizeof(coolapk.profile.brand), "Xiaomi");
    std::snprintf(coolapk.profile.model, sizeof(coolapk.profile.model), "24101PNB7C");
    std::snprintf(coolapk.profile.device, sizeof(coolapk.profile.device), "haotian");
    std::snprintf(coolapk.profile.product, sizeof(coolapk.profile.product), "haotian");
    map["com.coolapk.market"] = coolapk;

    return map;
}

void test_match_process_profile_logic() {
    const auto packages = create_sample_packages();
    DeviceProfile profile{};

    // Exact match
    EXPECT_TRUE(match_process_profile(packages, "com.ruanmei.ithome", &profile));
    EXPECT_STREQ(profile.manufacturer, "samsung");
    EXPECT_STREQ(profile.model, "SM-S9480");

    // Sub-process colon match
    std::memset(&profile, 0, sizeof(profile));
    EXPECT_TRUE(match_process_profile(packages, "com.ruanmei.ithome:push", &profile));
    EXPECT_STREQ(profile.manufacturer, "samsung");
    EXPECT_STREQ(profile.model, "SM-S9480");

    std::memset(&profile, 0, sizeof(profile));
    EXPECT_TRUE(match_process_profile(packages, "com.ruanmei.ithome:sandboxed_process0", &profile));
    EXPECT_STREQ(profile.brand, "samsung");
    EXPECT_STREQ(profile.device, "s26ultra");

    // Disabled package (exact and sub-process)
    EXPECT_FALSE(match_process_profile(packages, "com.coolapk.market", &profile));
    EXPECT_FALSE(match_process_profile(packages, "com.coolapk.market:download", &profile));

    // Non-existent package
    EXPECT_FALSE(match_process_profile(packages, "com.example.unrelated", &profile));

    // Edge cases
    EXPECT_FALSE(match_process_profile(packages, nullptr, &profile));
    EXPECT_FALSE(match_process_profile(packages, "", &profile));
    EXPECT_FALSE(match_process_profile(packages, "com.ruanmei.ithome:", &profile));
    EXPECT_FALSE(match_process_profile(packages, ":com.ruanmei.ithome", &profile));
    EXPECT_FALSE(match_process_profile(packages, ":", &profile));
}

void test_socket_ipc_protocol() {
    const char *temp_path = ".test-bin/test_companion_ipc.json";
    FILE *fp = std::fopen(temp_path, "w");
    EXPECT_TRUE(fp != nullptr);
    if (fp == nullptr) return;

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
    std::fputs(json, fp);
    std::fclose(fp);

    set_config_path(temp_path);
    reset_companion_cache();

    // 1. Matched package query over socket
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread server_thread([fds]() {
            companion_handler(fds[0]);
        });
        DeviceProfile out_profile{};
        bool success = query_profile_from_socket(fds[1], "com.ruanmei.ithome", &out_profile);
        server_thread.join();
        close(fds[1]);

        EXPECT_TRUE(success);
        EXPECT_STREQ(out_profile.manufacturer, "samsung");
        EXPECT_STREQ(out_profile.brand, "samsung");
        EXPECT_STREQ(out_profile.model, "SM-S9480");
        EXPECT_STREQ(out_profile.device, "s26ultra");
        EXPECT_STREQ(out_profile.product, "s26ultrachn");
    }

    // 2. Sub-process colon match over socket
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread server_thread([fds]() {
            companion_handler(fds[0]);
        });
        DeviceProfile out_profile{};
        bool success = query_profile_from_socket(fds[1], "com.ruanmei.ithome:push", &out_profile);
        server_thread.join();
        close(fds[1]);

        EXPECT_TRUE(success);
        EXPECT_STREQ(out_profile.model, "SM-S9480");
    }

    // 3. Disabled package over socket
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread server_thread([fds]() {
            companion_handler(fds[0]);
        });
        DeviceProfile out_profile{};
        bool success = query_profile_from_socket(fds[1], "com.coolapk.market", &out_profile);
        server_thread.join();
        close(fds[1]);

        EXPECT_FALSE(success);
    }

    // 4. Unmatched package over socket
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread server_thread([fds]() {
            companion_handler(fds[0]);
        });
        DeviceProfile out_profile{};
        bool success = query_profile_from_socket(fds[1], "com.unrelated.app", &out_profile);
        server_thread.join();
        close(fds[1]);

        EXPECT_FALSE(success);
    }

    // 5. Invalid / empty process name over socket
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        DeviceProfile out_profile{};
        EXPECT_FALSE(query_profile_from_socket(fds[1], nullptr, &out_profile));
        EXPECT_FALSE(query_profile_from_socket(fds[1], "", &out_profile));
        EXPECT_FALSE(query_profile_from_socket(-1, "com.ruanmei.ithome", &out_profile));
        close(fds[0]);
        close(fds[1]);
    }

    unlink(temp_path);
}

void test_config_file_reloading_and_cache() {
    const char *temp_path = ".test-bin/test_companion_reload.json";
    FILE *fp = std::fopen(temp_path, "w");
    EXPECT_TRUE(fp != nullptr);
    if (fp == nullptr) return;

    const char *json_v1 = R"({
        "version": 1,
        "packages": {
            "com.app.alpha": {
                "enabled": true,
                "model": "AlphaModelV1"
            }
        }
    })";
    std::fputs(json_v1, fp);
    std::fclose(fp);

    set_config_path(temp_path);
    reset_companion_cache();

    // Query Alpha
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread t([fds]() { companion_handler(fds[0]); });
        DeviceProfile profile{};
        EXPECT_TRUE(query_profile_from_socket(fds[1], "com.app.alpha", &profile));
        t.join();
        close(fds[1]);
        EXPECT_STREQ(profile.model, "AlphaModelV1");
    }

    // Query Beta (not yet present)
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread t([fds]() { companion_handler(fds[0]); });
        DeviceProfile profile{};
        EXPECT_FALSE(query_profile_from_socket(fds[1], "com.app.beta", &profile));
        t.join();
        close(fds[1]);
    }

    // Update config file with Beta and modified Alpha
    // Ensure mtime changes by setting timestamp to +2 seconds
    struct stat st;
    stat(temp_path, &st);
    struct timeval tv[2];
    tv[0].tv_sec = st.st_atime + 2;
    tv[0].tv_usec = 0;
    tv[1].tv_sec = st.st_mtime + 2;
    tv[1].tv_usec = 0;

    FILE *fp2 = std::fopen(temp_path, "w");
    EXPECT_TRUE(fp2 != nullptr);
    if (fp2 != nullptr) {
        const char *json_v2 = R"({
            "version": 1,
            "packages": {
                "com.app.alpha": {
                    "enabled": true,
                    "model": "AlphaModelV2"
                },
                "com.app.beta": {
                    "enabled": true,
                    "model": "BetaModelV1"
                }
            }
        })";
        std::fputs(json_v2, fp2);
        std::fclose(fp2);
        utimes(temp_path, tv);
    }

    // Query Alpha again -> should get V2
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread t([fds]() { companion_handler(fds[0]); });
        DeviceProfile profile{};
        EXPECT_TRUE(query_profile_from_socket(fds[1], "com.app.alpha", &profile));
        t.join();
        close(fds[1]);
        EXPECT_STREQ(profile.model, "AlphaModelV2");
    }

    // Query Beta -> should now succeed
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread t([fds]() { companion_handler(fds[0]); });
        DeviceProfile profile{};
        EXPECT_TRUE(query_profile_from_socket(fds[1], "com.app.beta", &profile));
        t.join();
        close(fds[1]);
        EXPECT_STREQ(profile.model, "BetaModelV1");
    }

    // Remove config file -> companion gracefully reports not found
    unlink(temp_path);
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread t([fds]() { companion_handler(fds[0]); });
        DeviceProfile profile{};
        EXPECT_FALSE(query_profile_from_socket(fds[1], "com.app.alpha", &profile));
        t.join();
        close(fds[1]);
    }
}

// Mock Zygisk API
struct MockZygiskContext {
    int companion_fd = -1;
};

int mock_connect_companion(void *impl) {
    if (impl == nullptr) return -1;
    auto *ctx = static_cast<MockZygiskContext *>(impl);
    return ctx->companion_fd;
}

void test_zygisk_api_query() {
    const char *temp_path = ".test-bin/test_companion_zygisk.json";
    FILE *fp = std::fopen(temp_path, "w");
    EXPECT_TRUE(fp != nullptr);
    if (fp == nullptr) return;

    const char *json = R"({
        "version": 1,
        "packages": {
            "com.test.zygisk": {
                "enabled": true,
                "model": "ZygiskModel"
            }
        }
    })";
    std::fputs(json, fp);
    std::fclose(fp);

    set_config_path(temp_path);
    reset_companion_cache();

    // 1. Success case with mock API
    {
        int fds[2];
        EXPECT_EQ(socketpair(AF_UNIX, SOCK_STREAM, 0, fds), 0);
        std::thread t([fds]() { companion_handler(fds[0]); });

        MockZygiskContext ctx{fds[1]};
        zygisk::internal::api_table tbl{};
        tbl.impl = &ctx;
        tbl.connectCompanion = mock_connect_companion;

        struct ApiAccessor {
            zygisk::internal::api_table *tbl;
        } accessor{&tbl};
        auto *api = reinterpret_cast<zygisk::Api *>(&accessor);

        DeviceProfile profile{};
        bool res = query_process_profile(api, "com.test.zygisk", &profile);
        t.join();

        EXPECT_TRUE(res);
        EXPECT_STREQ(profile.model, "ZygiskModel");
    }

    // 2. Failure when connectCompanion returns -1
    {
        MockZygiskContext ctx{-1};
        zygisk::internal::api_table tbl{};
        tbl.impl = &ctx;
        tbl.connectCompanion = mock_connect_companion;

        struct ApiAccessor {
            zygisk::internal::api_table *tbl;
        } accessor{&tbl};
        auto *api = reinterpret_cast<zygisk::Api *>(&accessor);

        DeviceProfile profile{};
        EXPECT_FALSE(query_process_profile(api, "com.test.zygisk", &profile));
    }

    // 3. Null API or null name
    {
        DeviceProfile profile{};
        EXPECT_FALSE(query_process_profile(nullptr, "com.test.zygisk", &profile));
        EXPECT_FALSE(query_process_profile(nullptr, nullptr, &profile));
    }

    unlink(temp_path);
}

}  // namespace

int main() {
    test_match_process_profile_logic();
    test_socket_ipc_protocol();
    test_config_file_reloading_and_cache();
    test_zygisk_api_query();
    return test_support::finish("companion_test");
}
