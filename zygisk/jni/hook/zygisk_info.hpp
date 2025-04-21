#include "zygisk.hpp"

void hook_zi_sfdex9008(zygisk::Api *api, const char *process);
void hook_dl_open(zygisk::Api *api, const char *process);
void hook_log(zygisk::Api *api, const char *process);
