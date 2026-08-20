#pragma once

namespace s26spoof {

struct LifecycleActions {
    bool target;
    bool unload_library;
    bool install_java_hook;
    bool install_native_hooks;
    bool write_build_fields;
};

LifecycleActions lifecycle_actions_for_process(const char *process_name) noexcept;
LifecycleActions lifecycle_actions_for_server() noexcept;

}  // namespace s26spoof
