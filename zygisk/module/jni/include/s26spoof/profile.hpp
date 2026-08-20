#pragma once

namespace s26spoof {

struct DeviceProfile {
    char manufacturer[64] = {};
    char brand[64] = {};
    char model[64] = {};
    char device[64] = {};
    char product[64] = {};
};

struct AppConfigEntry {
    bool enabled = false;
    char name[64] = {};
    DeviceProfile profile = {};
};

}  // namespace s26spoof
