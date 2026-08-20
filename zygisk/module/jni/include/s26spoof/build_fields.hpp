#pragma once

#include <stdint.h>
#include <jni.h>

#include "s26spoof/profile.hpp"

namespace s26spoof {

struct BuildWriteResult {
    uint8_t attempted;
    uint8_t succeeded;
    bool exception_cleared;
};

BuildWriteResult write_build_fields(JNIEnv *environment, const DeviceProfile &profile) noexcept;

}  // namespace s26spoof
