package com.sfdex.gmbioreactor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sfdex.gmbioreactor.data.model.BrandGroup
import com.sfdex.gmbioreactor.data.model.DeviceProfile
import com.sfdex.gmbioreactor.data.repository.ModelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for Model Library and Model Picker screens.
 */
data class ModelLibraryUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedBrand: String? = null,
    val brandGroups: List<BrandGroup> = emptyList(),
    val brandNames: List<String> = emptyList(),
    val customProfiles: List<DeviceProfile> = emptyList(),
    val displayedProfiles: List<DeviceProfile> = emptyList(),
    val editingProfile: DeviceProfile? = null,
    val isCreatingNew: Boolean = false,
    val statusMessage: String? = null,
    val isSuccessMessage: Boolean = true
)

class ModelLibraryViewModel(
    private val modelRepository: ModelRepository = ModelRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelLibraryUiState())
    val uiState: StateFlow<ModelLibraryUiState> = _uiState.asStateFlow()

    companion object {
        const val BRAND_ALL = "全部"
        const val BRAND_CUSTOM = "自定义"
    }

    init {
        loadData()
    }

    /**
     * Loads preset brand groups and custom user profiles.
     */
    fun loadData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val presets = modelRepository.getBrandGroups()
            val custom = modelRepository.getCustomProfiles()

            val brandNameList = mutableListOf(BRAND_ALL)
            if (custom.isNotEmpty()) {
                brandNameList.add(BRAND_CUSTOM)
            }
            brandNameList.addAll(presets.map { it.brandName })

            val currentSearch = _uiState.value.searchQuery
            val currentBrand = _uiState.value.selectedBrand
            val filtered = filterProfiles(presets, custom, currentSearch, currentBrand)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    brandGroups = presets,
                    brandNames = brandNameList,
                    customProfiles = custom,
                    displayedProfiles = filtered
                )
            }
        }
    }

    /**
     * Filters models with full-text search across name, manufacturer, brand, model, device, and product.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = filterProfiles(state.brandGroups, state.customProfiles, query, state.selectedBrand)
            state.copy(
                searchQuery = query,
                displayedProfiles = filtered
            )
        }
    }

    /**
     * Selects a brand filter chip ("全部", "自定义", or a specific brand name).
     */
    fun selectBrand(brandName: String?) {
        _uiState.update { state ->
            val normalizedBrand = if (brandName == BRAND_ALL) null else brandName
            val filtered = filterProfiles(state.brandGroups, state.customProfiles, state.searchQuery, normalizedBrand)
            state.copy(
                selectedBrand = normalizedBrand,
                displayedProfiles = filtered
            )
        }
    }

    /**
     * Saves or updates a custom device profile.
     */
    fun saveCustomProfile(profile: DeviceProfile): Boolean {
        if (!profile.isValid()) {
            _uiState.update {
                it.copy(
                    statusMessage = "请填写完整机型参数 (厂商/品牌/型号/设备名/产品名)",
                    isSuccessMessage = false
                )
            }
            return false
        }

        val success = modelRepository.saveCustomProfile(profile)
        if (success) {
            _uiState.update {
                it.copy(
                    editingProfile = null,
                    isCreatingNew = false,
                    statusMessage = "机型 [${profile.name.ifBlank { profile.model }}] 保存成功",
                    isSuccessMessage = true
                )
            }
            loadData()
            return true
        } else {
            _uiState.update {
                it.copy(
                    statusMessage = "保存机型失败，请重试",
                    isSuccessMessage = false
                )
            }
            return false
        }
    }

    /**
     * Deletes a custom device profile.
     */
    fun deleteCustomProfile(profileName: String): Boolean {
        val success = modelRepository.deleteCustomProfile(profileName)
        if (success) {
            _uiState.update {
                it.copy(
                    statusMessage = "已删除自定义机型: $profileName",
                    isSuccessMessage = true
                )
            }
            loadData()
            return true
        } else {
            _uiState.update {
                it.copy(
                    statusMessage = "删除失败: 未找到对应机型",
                    isSuccessMessage = false
                )
            }
            return false
        }
    }

    /**
     * Opens dialog for creating a brand-new device profile.
     */
    fun startCreateNewProfile() {
        _uiState.update {
            it.copy(
                isCreatingNew = true,
                editingProfile = DeviceProfile()
            )
        }
    }

    /**
     * Opens dialog for editing an existing device profile.
     */
    fun startEditProfile(profile: DeviceProfile) {
        _uiState.update {
            it.copy(
                isCreatingNew = false,
                editingProfile = profile
            )
        }
    }

    /**
     * Dismisses the profile create/edit dialog.
     */
    fun dismissEditDialog() {
        _uiState.update {
            it.copy(
                isCreatingNew = false,
                editingProfile = null
            )
        }
    }

    /**
     * Clears temporary snackbar status message.
     */
    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun filterProfiles(
        brandGroups: List<BrandGroup>,
        customProfiles: List<DeviceProfile>,
        query: String,
        selectedBrand: String?
    ): List<DeviceProfile> {
        val trimmed = query.trim()

        val baseList: List<DeviceProfile> = when {
            selectedBrand == null || selectedBrand == BRAND_ALL || selectedBrand.isBlank() -> {
                customProfiles + brandGroups.flatMap { it.models }
            }
            selectedBrand == BRAND_CUSTOM -> {
                customProfiles
            }
            else -> {
                brandGroups.find { it.brandName.equals(selectedBrand, ignoreCase = true) }?.models ?: emptyList()
            }
        }

        if (trimmed.isEmpty()) {
            return baseList
        }

        return baseList.filter { profile ->
            profile.name.contains(trimmed, ignoreCase = true) ||
                    profile.manufacturer.contains(trimmed, ignoreCase = true) ||
                    profile.brand.contains(trimmed, ignoreCase = true) ||
                    profile.model.contains(trimmed, ignoreCase = true) ||
                    profile.device.contains(trimmed, ignoreCase = true) ||
                    profile.product.contains(trimmed, ignoreCase = true)
        }
    }
}
