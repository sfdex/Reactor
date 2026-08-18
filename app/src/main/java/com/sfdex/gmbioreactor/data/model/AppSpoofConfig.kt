package com.sfdex.gmbioreactor.data.model

data class AppSpoofConfig(
    val packageName: String,
    val enabled: Boolean = true,
    val profile: DeviceProfile = DeviceProfile()
)
