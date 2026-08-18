package com.sfdex.gmbioreactor.data.model

data class BrandGroup(
    val brandName: String,
    val models: List<DeviceProfile> = emptyList()
)
