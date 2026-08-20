#include <android/log.h>
#include <dlfcn.h>
#include <stddef.h>
#include <stdio.h>
#include <string>

#include "s26spoof/build_fields.hpp"
#include "s26spoof/companion.hpp"
#include "s26spoof/maps.hpp"
#include "s26spoof/native_hooks.hpp"
#include "s26spoof/profile.hpp"
#include "s26spoof/property_hooks.hpp"
#include "zygisk.hpp"

namespace {

constexpr char kLogTag[] = "GMBioreactor";

bool clear_module_exception(JNIEnv *environment) {
    if (environment == nullptr || !environment->ExceptionCheck()) return false;
    environment->ExceptionClear();
    return true;
}

bool install_java_property_hook(zygisk::Api *api, JNIEnv *environment) {
    if (api == nullptr || environment == nullptr) return false;
    if (environment->ExceptionCheck()) return false;
    JNINativeMethod methods[] = {
        {"native_get", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
         reinterpret_cast<void *>(s26spoof::hooked_java_property_get)},
    };
    api->hookJniNativeMethods(environment, "android/os/SystemProperties", methods, 1);
    if (clear_module_exception(environment)) return false;
    if (methods[0].fnPtr == nullptr) return false;
    s26spoof::set_original_java_property_get(
        reinterpret_cast<s26spoof::JavaPropertyGetFn>(methods[0].fnPtr));
    return true;
}

void register_zygisk_hook(void *context, dev_t device, ino_t inode,
                          const char *symbol, void *replacement,
                          void **original) {
    auto *api = static_cast<zygisk::Api *>(context);
    api->pltHookRegister(device, inode, symbol, replacement, original);
}

bool commit_zygisk_hooks(void *context) {
    return static_cast<zygisk::Api *>(context)->pltHookCommit();
}

void *resolve_bionic_symbol(const char *symbol) {
    return dlsym(RTLD_DEFAULT, symbol);
}

s26spoof::NativeHookStatus install_native_property_hooks(zygisk::Api *api) {
    if (api == nullptr) return {};
    FILE *maps_file = fopen("/proc/self/maps", "r");
    if (maps_file == nullptr) return {};

    s26spoof::MapIdentity mapped_files[s26spoof::kMaximumMappedFiles] = {};
    size_t mapped_file_count = 0;
    char line[1024] = {};
    while (fgets(line, sizeof(line), maps_file) != nullptr) {
        s26spoof::MapIdentity identity{};
        if (!s26spoof::parse_map_identity(line, &identity) || !identity.executable) {
            continue;
        }
        s26spoof::remember_unique_map(identity, mapped_files, &mapped_file_count,
                                     s26spoof::kMaximumMappedFiles);
    }
    fclose(maps_file);
    if (mapped_file_count == 0) return {};

    const s26spoof::NativeHookApi hook_api{
        api,
        register_zygisk_hook,
        commit_zygisk_hooks,
    };
    return s26spoof::install_native_property_hooks(
        hook_api, mapped_files, mapped_file_count, resolve_bionic_symbol);
}

class GMBioreactorModule final : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *environment) override {
        api_ = api;
        environment_ = environment;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        is_target_ = false;
        process_name_.clear();
        active_profile_ = {};

        const jstring process_name = args == nullptr ? nullptr : args->nice_name;
        if (environment_ != nullptr && process_name != nullptr &&
            !environment_->ExceptionCheck()) {
            const char *process_chars =
                environment_->GetStringUTFChars(process_name, nullptr);
            const bool access_failed = environment_->ExceptionCheck();
            if (!access_failed && process_chars != nullptr) {
                is_target_ = s26spoof::query_process_profile(
                    api_, process_chars, &active_profile_);
                if (is_target_) {
                    process_name_ = process_chars;
                }
            }
            if (process_chars != nullptr) {
                environment_->ReleaseStringUTFChars(process_name, process_chars);
            }
            if (access_failed) clear_module_exception(environment_);
        }

        if (!is_target_) {
            if (api_ != nullptr) api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        s26spoof::set_active_profile(&active_profile_);
        java_hooked_ = install_java_property_hook(api_, environment_);
        native_hooks_ = install_native_property_hooks(api_);
    }

    void preServerSpecialize(zygisk::ServerSpecializeArgs *) override {
        if (api_ != nullptr) {
            api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!is_target_) return;
        const s26spoof::BuildWriteResult result =
            s26spoof::write_build_fields(environment_, active_profile_);
        __android_log_print(
            ANDROID_LOG_INFO, kLogTag,
            "target=1 pkg=%s model=%s java=%d native=%d fields=%u/%u",
            process_name_.c_str(), active_profile_.model,
            java_hooked_ ? 1 : 0,
            (native_hooks_.property_get && native_hooks_.property_read_callback) ? 1 : 0,
            static_cast<unsigned int>(result.succeeded),
            static_cast<unsigned int>(result.attempted));
    }

private:
    zygisk::Api *api_ = nullptr;
    JNIEnv *environment_ = nullptr;
    bool is_target_ = false;
    std::string process_name_;
    s26spoof::DeviceProfile active_profile_{};
    bool java_hooked_ = false;
    s26spoof::NativeHookStatus native_hooks_{};
};

}  // namespace

REGISTER_ZYGISK_MODULE(GMBioreactorModule)
REGISTER_ZYGISK_COMPANION(s26spoof::companion_handler)
