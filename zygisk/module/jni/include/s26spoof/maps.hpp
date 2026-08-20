#pragma once

#include <stddef.h>
#include <sys/types.h>

namespace s26spoof {

struct MapIdentity {
    dev_t device;
    ino_t inode;
    bool executable;
};

bool parse_map_identity(const char *line, MapIdentity *result) noexcept;

bool remember_unique_map(MapIdentity value, MapIdentity *values,
                         size_t *count, size_t capacity) noexcept;

}  // namespace s26spoof
