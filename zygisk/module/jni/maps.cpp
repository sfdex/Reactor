#include "s26spoof/maps.hpp"

#include <stdio.h>
#include <string.h>

#if defined(__linux__)
#include <sys/sysmacros.h>
#endif

namespace s26spoof {

bool parse_map_identity(const char *line, MapIdentity *result) noexcept {
    if (line == nullptr || result == nullptr) return false;

    char permissions[5] = {};
    unsigned int device_major = 0;
    unsigned int device_minor = 0;
    unsigned long inode = 0;
    const int fields = sscanf(line, "%*lx-%*lx %4s %*lx %x:%x %lu", permissions,
                              &device_major, &device_minor, &inode);
    if (fields != 4 || inode == 0) return false;

    result->device = makedev(device_major, device_minor);
    result->inode = static_cast<ino_t>(inode);
    result->executable = strchr(permissions, 'x') != nullptr;
    return true;
}

bool remember_unique_map(MapIdentity value, MapIdentity *values,
                         size_t *count, size_t capacity) noexcept {
    if (values == nullptr || count == nullptr || *count > capacity) return false;
    for (size_t index = 0; index < *count; ++index) {
        if (values[index].device == value.device && values[index].inode == value.inode) {
            return false;
        }
    }
    if (*count == capacity) return false;
    values[*count] = value;
    ++(*count);
    return true;
}

}  // namespace s26spoof
