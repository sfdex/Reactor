package com.sfdex.reactor.data.model

data class BrandGroup(
    val brandName: String,
    val models: List<DeviceProfile> = emptyList()
)
