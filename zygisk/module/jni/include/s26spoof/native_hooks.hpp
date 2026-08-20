#pragma once

#include <stddef.h>
#include <sys/types.h>

#include "s26spoof/maps.hpp"

namespace s26spoof {

inline constexpr size_t kMaximumMappedFiles = 512;

using NativeSymbolResolver = void *(*)(const char *symbol);
using PltHookRegisterFn = void (*)(void *context, dev_t device, ino_t inode,
                                   const char *symbol, void *replacement,
                                   void **original);
using PltHookCommitFn = bool (*)(void *context);

struct NativeHookApi {
    void *context;
    PltHookRegisterFn register_hook;
    PltHookCommitFn commit_hooks;
};

struct NativeHookStatus {
    bool fallbacks_ready;
    bool commit_succeeded;
    bool property_get;
    bool property_read_callback;
};

NativeHookStatus install_native_property_hooks(
    NativeHookApi api, const MapIdentity *mapped_files, size_t mapped_file_count,
    NativeSymbolResolver resolver) noexcept;

}  // namespace s26spoof
