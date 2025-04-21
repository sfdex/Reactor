//
// Created by sfdex on 4/19/25.
//
#include <fcntl.h>
#include <dlfcn.h>
#include "zygisk_info.hpp"
#include "log.hpp"

int proxy_sfdex9008(const char *info) {
    LOGD("Hello ZygiskInfo sfdex9008, params=%s", info);
    __android_log_print(ANDROID_LOG_DEBUG, "ZygiskInfoH", "entry: sfdex9008(%s)", info);
    return 20;
}

/**
 * /data/app/~~9Lbg2mfaBvtg2rif83z7AQ==/com.sfdex.zygiskinfo-tYCd-bQoEjmGjv57CRpIPA==/base.apk!/lib/arm64-v8a/libnativeget.so
 **/
void hook_zi_sfdex9008(zygisk::Api *api, const char *process) {
    LOGD("hook_zi_sfdex9008 %s, pltHook start", process);

    struct stat st{};
    if (stat(
            "/data/app/~~9Lbg2mfaBvtg2rif83z7AQ==/com.sfdex.zygiskinfo-tYCd-bQoEjmGjv57CRpIPA==/base.apk!/lib/arm64-v8a/libnativeget.so",
            &st) == 0) {
        dev_t dev = st.st_dev;
        ino_t inode = st.st_ino;
        LOGD("hook_zi_sfdex9008 stat libnativeget.so, dev=%u, inode=%ld", dev, inode);
        api->pltHookRegister(dev, inode, "sfdex9008",
                             (void *) proxy_sfdex9008,
                             nullptr);
    }
}

void *_Nullable orig_dlopen(const char *_Nullable __filename, int __flag);

void *_Nullable my_dlopen(const char *_Nullable __filename, int __flag) {
    LOGD("my_dlopen, filename=%s, flag=%d", __filename, __flag);
    void *ptr = dlopen(__filename, __flag);
    LOGD("my_dlopen, ptr=%d", ptr);
    return ptr;
}

void hook_dl_open(zygisk::Api *api, const char *process) {

    LOGD("hook_dl_open %s, pltHook start", process);

    struct stat st{};
    if (stat("/system/lib64/libc.so", &st) == 0) {
        dev_t dev = st.st_dev;
        ino_t inode = st.st_ino;
        LOGD("hook_dl_open stat libc.so, dev=%u, inode=%ld", dev, inode);
        api->pltHookRegister(dev, inode, "dlopen",
                             (void *) my_dlopen,
                             nullptr);
    }
}

/*
static jint (*orig_logger_entry_max)(JNIEnv *env);

static jint my_logger_entry_max(JNIEnv *env) {
    return orig_logger_entry_max(env);
}

void hook_log(zygisk::Api *api, const char *process) {
    JNINativeMethod methods[] = {
            { "logger_entry_max_payload_native", "()I", (void*) my_logger_entry_max },
    };
    api->hookJniNativeMethods(env, "android/util/Log", methods, 1);
    *(void **) &orig_logger_entry_max = methods[0].fnPtr;
}*/
