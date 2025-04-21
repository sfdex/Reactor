//
// Created by sfdex on 4/18/25.
//
#include <jni.h>
#include <string>
#include <sys/system_properties.h>
#include "system_property.hpp"
#include "log.hpp"

#define PROP_VALUE_MAX  92

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

void hook_system_property_read_callback(zygisk::Api *api, const char *process) {

    int apiLevel = android_get_device_api_level();
#if apiLevel >= 26
    LOGD("Hello api 26+");
#else
    LOGD("Android Api: %d", apiLevel);
#endif
    api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    LOGD("hook_system_property_read_callback %s, pltHook start", process);

    struct stat st{};
//    if (stat("/apex/com.android.runtime/lib64/bionic/libc.so", &st) == 0) {
//    if (stat("/system/lib64/libandroid_runtime.so", &st) == 0) {
    if (stat("/system/lib64/libandroid.so", &st) == 0) {
        dev_t dev = st.st_dev;
        ino_t inode = st.st_ino;
        LOGD("hook_system_property_read_callback stat libc.so, dev=%u, inode=%ld", dev, inode);
        api->pltHookRegister(dev, inode, "__system_property_read_callback",
                             (void *) proxy_system_property_read_callback,
                             nullptr);
    }
}
