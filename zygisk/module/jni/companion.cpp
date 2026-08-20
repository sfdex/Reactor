#include "s26spoof/companion.hpp"

#include <errno.h>
#include <fcntl.h>
#include <mutex>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

#include "s26spoof/config_parser.hpp"
#include "zygisk.hpp"

namespace s26spoof {
namespace {

std::mutex g_config_mutex;
std::string g_config_path = kDefaultConfigPath;
time_t g_last_mtime = 0;
off_t g_last_size = 0;
bool g_has_cached_config = false;
std::unordered_map<std::string, AppConfigEntry> g_cached_packages;

bool write_exact(int fd, const void *data, size_t size) noexcept {
    if (fd < 0 || data == nullptr) return false;
    const uint8_t *ptr = static_cast<const uint8_t *>(data);
    size_t remaining = size;
    while (remaining > 0) {
#if defined(MSG_NOSIGNAL)
        ssize_t written = send(fd, ptr, remaining, MSG_NOSIGNAL);
#else
        ssize_t written = write(fd, ptr, remaining);
#endif
        if (written < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (written == 0) return false;
        ptr += written;
        remaining -= static_cast<size_t>(written);
    }
    return true;
}

bool read_exact(int fd, void *data, size_t size) noexcept {
    if (fd < 0 || data == nullptr) return false;
    uint8_t *ptr = static_cast<uint8_t *>(data);
    size_t remaining = size;
    while (remaining > 0) {
        ssize_t bytes_read = read(fd, ptr, remaining);
        if (bytes_read < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (bytes_read == 0) return false;
        ptr += bytes_read;
        remaining -= static_cast<size_t>(bytes_read);
    }
    return true;
}

bool read_file_to_string(const char *path, std::string *out) {
    if (path == nullptr || out == nullptr) return false;
    FILE *fp = fopen(path, "rb");
    if (fp == nullptr) return false;

    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);
    fseek(fp, 0, SEEK_SET);

    if (size < 0) {
        fclose(fp);
        return false;
    }

    std::string content;
    content.resize(static_cast<size_t>(size));
    size_t read_bytes = fread(&content[0], 1, static_cast<size_t>(size), fp);
    fclose(fp);

    if (read_bytes != static_cast<size_t>(size)) {
        return false;
    }
    *out = std::move(content);
    return true;
}

void reload_config_locked() {
    struct stat st;
    if (stat(g_config_path.c_str(), &st) != 0) {
        g_cached_packages.clear();
        g_last_mtime = 0;
        g_has_cached_config = false;
        return;
    }

    if (g_has_cached_config && st.st_mtime == g_last_mtime && st.st_size == g_last_size) {
        return;
    }

    std::string content;
    if (!read_file_to_string(g_config_path.c_str(), &content)) {
        g_cached_packages.clear();
        g_last_mtime = 0;
        g_last_size = 0;
        g_has_cached_config = false;
        return;
    }

    std::unordered_map<std::string, AppConfigEntry> new_map;
    if (!parse_config_json(content.c_str(), &new_map)) {
        g_cached_packages.clear();
        g_last_mtime = 0;
        g_last_size = 0;
        g_has_cached_config = false;
        return;
    }

    g_cached_packages = std::move(new_map);
    g_last_mtime = st.st_mtime;
    g_last_size = st.st_size;
    g_has_cached_config = true;
}

}  // namespace

void set_config_path(const char *path) noexcept {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    if (path == nullptr) {
        g_config_path = kDefaultConfigPath;
    } else {
        g_config_path = path;
    }
    g_last_mtime = 0;
    g_last_size = 0;
    g_has_cached_config = false;
    g_cached_packages.clear();
}

const char *get_config_path() noexcept {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    return g_config_path.c_str();
}

void reset_companion_cache() noexcept {
    std::lock_guard<std::mutex> lock(g_config_mutex);
    g_last_mtime = 0;
    g_last_size = 0;
    g_has_cached_config = false;
    g_cached_packages.clear();
}

bool match_process_profile(
    const std::unordered_map<std::string, AppConfigEntry> &packages,
    const char *process_name,
    DeviceProfile *out_profile) noexcept {
    if (process_name == nullptr || process_name[0] == '\0') {
        return false;
    }

    // 1. Exact match
    auto it = packages.find(process_name);
    if (it != packages.end()) {
        if (it->second.enabled) {
            if (out_profile != nullptr) {
                *out_profile = it->second.profile;
            }
            return true;
        }
        return false;
    }

    // 2. Sub-process match (prefix before ':')
    const char *colon = strchr(process_name, ':');
    if (colon != nullptr && colon != process_name && colon[1] != '\0') {
        std::string base_pkg(process_name, static_cast<size_t>(colon - process_name));
        auto it2 = packages.find(base_pkg);
        if (it2 != packages.end()) {
            if (it2->second.enabled) {
                if (out_profile != nullptr) {
                    *out_profile = it2->second.profile;
                }
                return true;
            }
            return false;
        }
    }

    return false;
}

void companion_handler(int socket_fd) noexcept {
    if (socket_fd < 0) return;

    uint32_t name_len = 0;
    if (!read_exact(socket_fd, &name_len, sizeof(name_len)) ||
        name_len == 0 || name_len > 256) {
        uint8_t status = 0;
        write_exact(socket_fd, &status, sizeof(status));
        close(socket_fd);
        return;
    }

    char process_name[257] = {};
    if (!read_exact(socket_fd, process_name, name_len)) {
        uint8_t status = 0;
        write_exact(socket_fd, &status, sizeof(status));
        close(socket_fd);
        return;
    }
    process_name[name_len] = '\0';

    DeviceProfile matched_profile{};
    bool matched = false;

    {
        std::lock_guard<std::mutex> lock(g_config_mutex);
        reload_config_locked();
        matched = match_process_profile(g_cached_packages, process_name, &matched_profile);
    }

    if (matched) {
        uint8_t status = 1;
        if (write_exact(socket_fd, &status, sizeof(status))) {
            write_exact(socket_fd, &matched_profile, sizeof(matched_profile));
        }
    } else {
        uint8_t status = 0;
        write_exact(socket_fd, &status, sizeof(status));
    }

    close(socket_fd);
}

bool query_profile_from_socket(
    int socket_fd,
    const char *process_name,
    DeviceProfile *out_profile) noexcept {
    if (socket_fd < 0 || process_name == nullptr || process_name[0] == '\0') {
        return false;
    }

    const size_t name_len_sz = strlen(process_name);
    if (name_len_sz == 0 || name_len_sz > 256) {
        return false;
    }

    const uint32_t name_len = static_cast<uint32_t>(name_len_sz);
    if (!write_exact(socket_fd, &name_len, sizeof(name_len))) {
        return false;
    }
    if (!write_exact(socket_fd, process_name, name_len)) {
        return false;
    }

    uint8_t status = 0;
    if (!read_exact(socket_fd, &status, sizeof(status))) {
        return false;
    }

    if (status != 1) {
        return false;
    }

    DeviceProfile profile{};
    if (!read_exact(socket_fd, &profile, sizeof(profile))) {
        return false;
    }

    if (out_profile != nullptr) {
        *out_profile = profile;
    }
    return true;
}

bool query_process_profile(
    zygisk::Api *api,
    const char *process_name,
    DeviceProfile *out_profile) noexcept {
    if (api == nullptr || process_name == nullptr || out_profile == nullptr) {
        return false;
    }

    int socket_fd = api->connectCompanion();
    if (socket_fd < 0) {
        return false;
    }

    bool result = query_profile_from_socket(socket_fd, process_name, out_profile);
    close(socket_fd);
    return result;
}

}  // namespace s26spoof
