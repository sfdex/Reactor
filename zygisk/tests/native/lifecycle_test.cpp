#include "s26spoof/lifecycle.hpp"
#include "test_support.hpp"

namespace {

using s26spoof::LifecycleActions;
using s26spoof::lifecycle_actions_for_process;
using s26spoof::lifecycle_actions_for_server;

void target_process_keeps_library_and_enables_every_layer() {
    const LifecycleActions main =
        lifecycle_actions_for_process("com.ruanmei.ithome");
    EXPECT_TRUE(main.target);
    EXPECT_FALSE(main.unload_library);
    EXPECT_TRUE(main.install_java_hook);
    EXPECT_TRUE(main.install_native_hooks);
    EXPECT_TRUE(main.write_build_fields);

    const LifecycleActions child =
        lifecycle_actions_for_process("com.ruanmei.ithome:web");
    EXPECT_TRUE(child.target);
    EXPECT_FALSE(child.unload_library);
    EXPECT_TRUE(child.install_java_hook);
    EXPECT_TRUE(child.install_native_hooks);
    EXPECT_TRUE(child.write_build_fields);
}

void non_target_process_unloads_without_installing_anything() {
    const LifecycleActions other =
        lifecycle_actions_for_process("com.example.other");
    EXPECT_FALSE(other.target);
    EXPECT_TRUE(other.unload_library);
    EXPECT_FALSE(other.install_java_hook);
    EXPECT_FALSE(other.install_native_hooks);
    EXPECT_FALSE(other.write_build_fields);

    const LifecycleActions malformed =
        lifecycle_actions_for_process("com.ruanmei.ithome:");
    EXPECT_FALSE(malformed.target);
    EXPECT_TRUE(malformed.unload_library);
    EXPECT_FALSE(malformed.install_java_hook);
    EXPECT_FALSE(malformed.install_native_hooks);
    EXPECT_FALSE(malformed.write_build_fields);
}

void system_server_always_unloads_without_installing_anything() {
    const LifecycleActions server = lifecycle_actions_for_server();
    EXPECT_FALSE(server.target);
    EXPECT_TRUE(server.unload_library);
    EXPECT_FALSE(server.install_java_hook);
    EXPECT_FALSE(server.install_native_hooks);
    EXPECT_FALSE(server.write_build_fields);
}

}  // namespace

int main() {
    target_process_keeps_library_and_enables_every_layer();
    non_target_process_unloads_without_installing_anything();
    system_server_always_unloads_without_installing_anything();
    return test_support::finish("lifecycle_test");
}
