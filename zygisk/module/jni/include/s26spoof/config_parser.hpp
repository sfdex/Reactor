#pragma once

#include <string>
#include <unordered_map>

#include "s26spoof/profile.hpp"

namespace s26spoof {

bool parse_config_json(const char *json,
                       std::unordered_map<std::string, AppConfigEntry> *out_packages);

}  // namespace s26spoof
