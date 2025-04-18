#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <string>
#include <android/log.h>
#include <sys/system_properties.h>

#include "zygisk.hpp"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "MyModule", __VA_ARGS__)

struct prop_info {
    std::atomic_uint_least32_t serial;
    char value[PROP_VALUE_MAX];
    char name[0];
};

using func_callback = void(void *cookie, const char *name, const char *value, uint32_t serial);

void proxy_system_property_read_callback(const prop_info *pi, func_callback *callback_func,
                                         void *cookie) {
    LOGD("proxy_system_property_read_callback [%s]: [%s]", pi->name, pi->value);

    /*if (strstr(pi->name, "ro.product.model")
        || strstr(pi->name, "ro.product.brand")
        || strstr(pi->name, "ro.product.manufacturer")) {


        char *new_value = "";
        return callbackFunc(cookie, pi->name, new_value, pi->serial);
    }*/

    return callback_func(cookie, pi->name, pi->value, pi->serial);
}

class MyModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        // Use JNI to fetch our process name
        const char *process = env->GetStringUTFChars(args->nice_name, nullptr);
        preSpecialize(process);
        env->ReleaseStringUTFChars(args->nice_name, process);
    }

    void preServerSpecialize(ServerSpecializeArgs *args) override {
        preSpecialize("system_server");
    }

private:
    Api *api;
    JNIEnv *env;

    void preSpecialize(const char *process) {
        // Demonstrate connecting to to companion process
        // We ask the companion for a random number
        unsigned r = 0;
        int fd = api->connectCompanion();
        read(fd, &r, sizeof(r));
        close(fd);
        LOGD("process=[%s], r=[%u]\n", process, r);

        hook_system_property_read_callback(process);

        // Since we do not hook any functions, we should let Zygisk dlclose ourselves
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }

    void hook_system_property_read_callback(const char *process) {
        int apiLevel = android_get_device_api_level();
#if apiLevel >= 26
        LOGD("Hello api 26+");
#else
        LOGD("Android Api: %d", apiLevel);
#endif
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
        LOGD("hook_system_property_read_callback %s, pltHook start", process);

        struct stat st{};
        if (stat("/apex/com.android.runtime/lib64/bionic/libc.so", &st) == 0) {
            dev_t dev = st.st_dev;
            ino_t inode = st.st_ino;
            LOGD("hook_system_property_read_callback stat libc.so, dev=%ld, inode=%ld", dev, inode);
            api->pltHookRegister(dev, inode, "__system_property_read_callback",
                                 (void *) proxy_system_property_read_callback,
                                 nullptr);
        }

        bool result = api->pltHookCommit();
        LOGD("hook_system_property_read_callback %s, pltHook result: %d", process, result);
    }
};

static int urandom = -1;

static void companion_handler(int i) {
    if (urandom < 0) {
        urandom = open("/dev/urandom", O_RDONLY);
    }
    unsigned r;
    read(urandom, &r, sizeof(r));
    LOGD("companion r=[%u]\n", r);
    write(i, &r, sizeof(r));
}

// Register our module class and the companion handler function
REGISTER_ZYGISK_MODULE(MyModule)

REGISTER_ZYGISK_COMPANION(companion_handler)