#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <string>
#include <sys/system_properties.h>

#include "zygisk.hpp"
#include "hook.hpp"
#include "log.hpp"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

std::atomic_uint_least32_t serial;

static jint (*orig_logger_entry_max)(JNIEnv *env);

static jint my_logger_entry_max(JNIEnv *env) {
    LOGD("my_logger LOG");
    return orig_logger_entry_max(env);
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
        LOGD("preSpecialize process=[%s], r=[%u]\n", process, r);

        //if (!strcmp(process, "system_server")) {
            LOGD("hook start");
            hook(api, process);
            LOGD("hook finish");
        //}

        LOGD("hook jni start");
        JNINativeMethod methods[] = {
                { "logger_entry_max_payload_native", "()I", (void*) my_logger_entry_max },
        };
        api->hookJniNativeMethods(env, "android/util/Log", methods, 1);
        *(void **) &orig_logger_entry_max = methods[0].fnPtr;
        LOGD("hook jni finish");

        bool result = api->pltHookCommit();
        LOGD("hook result in %s, pltHook result: %d", process, result);

        // Since we do not hook any functions, we should let Zygisk dlclose ourselves
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
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