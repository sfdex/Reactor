#pragma once

#include <string>
#include <unordered_map>

#include "s26spoof/profile.hpp"

namespace zygisk {
struct Api;
}

namespace s26spoof {

constexpr const char *kDefaultConfigPath = "/data/adb/gmbioreactor/config.json";

void set_config_path(const char *path) noexcept;
const char *get_config_path() noexcept;
void reset_companion_cache() noexcept;

bool match_process_profile(
    const std::unordered_map<std::string, AppConfigEntry> &packages,
    const char *process_name,
    DeviceProfile *out_profile) noexcept;

bool query_profile_from_socket(
    int socket_fd,
    const char *process_name,
    DeviceProfile *out_profile) noexcept;

bool query_process_profile(
    zygisk::Api *api,
    const char *process_name,
    DeviceProfile *out_profile) noexcept;

void companion_handler(int socket_fd) noexcept;

}  // namespace s26spoof
