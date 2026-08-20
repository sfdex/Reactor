#pragma once

#include <stddef.h>

#include "s26spoof/profile.hpp"

namespace s26spoof {

bool is_target_process(const char *name) noexcept;

const char *find_property_override(const DeviceProfile &profile, const char *key) noexcept;

int copy_property_value(const char *value, char *destination,
                        size_t capacity) noexcept;

}  // namespace s26spoof
