//
// Created by sfdex on 4/18/25.
//

#ifndef REACTOR_SYSTEM_PROPERTY_HPP
#define REACTOR_SYSTEM_PROPERTY_HPP

#include "zygisk.hpp"

void hook_system_property_read_callback(zygisk::Api *api, const char *process);

#endif //REACTOR_SYSTEM_PROPERTY_HPP
