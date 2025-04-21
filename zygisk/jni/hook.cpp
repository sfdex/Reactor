//
// Created by sfdex on 4/18/25.
//
#include "string"
#include "hook.hpp"
#include "hook/system_property.hpp"
#include "hook/zygisk_info.hpp"
#include "log.hpp"

void hook(zygisk::Api *api, const char *process) {
    LOGD("hook entry start");
    hook_system_property_read_callback(api, process);
    if (strstr(process, "com.sfdex.zygiskinfo")) {
        hook_zi_sfdex9008(api, process);
        hook_dl_open(api, process);
    }
    LOGD("hook entry finish");
}