package com.sfdex.reactor.data.model

data class DeviceProfile(
    val name: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val model: String = "",
    val device: String = "",
    val product: String = ""
) {
    fun isValid(): Boolean {
        return manufacturer.isNotBlank() &&
                brand.isNotBlank() &&
                model.isNotBlank() &&
                device.isNotBlank() &&
                product.isNotBlank()
    }
}
