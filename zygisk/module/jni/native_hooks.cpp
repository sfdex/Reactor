#include "s26spoof/native_hooks.hpp"

#include <string.h>

#include "s26spoof/property_hooks.hpp"

namespace s26spoof {
namespace {

constexpr char kPropertyGetSymbol[] = "__system_property_get";
constexpr char kPropertyReadCallbackSymbol[] =
    "__system_property_read_callback";

bool valid_original(void *candidate, void *replacement) noexcept {
    return candidate != nullptr && candidate != replacement;
}

}  // namespace

NativeHookStatus install_native_property_hooks(
    NativeHookApi api, const MapIdentity *mapped_files, size_t mapped_file_count,
    NativeSymbolResolver resolver) noexcept {
    NativeHookStatus status{};
    if (api.register_hook == nullptr || api.commit_hooks == nullptr ||
        mapped_files == nullptr || mapped_file_count == 0 ||
        mapped_file_count > kMaximumMappedFiles || resolver == nullptr) {
        return status;
    }

    void *fallback_get = resolver(kPropertyGetSymbol);
    void *fallback_read = resolver(kPropertyReadCallbackSymbol);
    if (!valid_original(fallback_get,
                        reinterpret_cast<void *>(hooked_property_get)) ||
        !valid_original(
            fallback_read,
            reinterpret_cast<void *>(hooked_property_read_callback))) {
        return status;
    }

    set_original_property_get(reinterpret_cast<PropertyGetFn>(fallback_get));
    set_original_property_read_callback(
        reinterpret_cast<PropertyReadCallbackFn>(fallback_read));
    status.fallbacks_ready = true;

    void *property_get_originals[kMaximumMappedFiles] = {};
    void *property_read_originals[kMaximumMappedFiles] = {};
    bool registered = false;
    for (size_t index = 0; index < mapped_file_count; ++index) {
        const MapIdentity &identity = mapped_files[index];
        if (!identity.executable) continue;
        api.register_hook(api.context, identity.device, identity.inode,
                          kPropertyGetSymbol,
                          reinterpret_cast<void *>(hooked_property_get),
                          &property_get_originals[index]);
        api.register_hook(
            api.context, identity.device, identity.inode,
            kPropertyReadCallbackSymbol,
            reinterpret_cast<void *>(hooked_property_read_callback),
            &property_read_originals[index]);
        registered = true;
    }
    if (!registered) return status;

    status.commit_succeeded = api.commit_hooks(api.context);
    if (!status.commit_succeeded) return status;

    for (size_t index = 0; index < mapped_file_count; ++index) {
        if (!valid_original(
                property_get_originals[index],
                reinterpret_cast<void *>(hooked_property_get))) {
            continue;
        }
        set_original_property_get(
            reinterpret_cast<PropertyGetFn>(property_get_originals[index]));
        status.property_get = true;
        break;
    }
    for (size_t index = 0; index < mapped_file_count; ++index) {
        if (!valid_original(
                property_read_originals[index],
                reinterpret_cast<void *>(hooked_property_read_callback))) {
            continue;
        }
        set_original_property_read_callback(
            reinterpret_cast<PropertyReadCallbackFn>(
                property_read_originals[index]));
        status.property_read_callback = true;
        break;
    }
    return status;
}

}  // namespace s26spoof
