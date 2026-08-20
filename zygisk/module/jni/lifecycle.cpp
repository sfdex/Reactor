#include "s26spoof/lifecycle.hpp"

#include "s26spoof/core.hpp"

namespace s26spoof {

LifecycleActions lifecycle_actions_for_process(const char *process_name) noexcept {
    const bool target = is_target_process(process_name);
    return LifecycleActions{
        target,
        !target,
        target,
        target,
        target,
    };
}

LifecycleActions lifecycle_actions_for_server() noexcept {
    return LifecycleActions{
        false,
        true,
        false,
        false,
        false,
    };
}

}  // namespace s26spoof
