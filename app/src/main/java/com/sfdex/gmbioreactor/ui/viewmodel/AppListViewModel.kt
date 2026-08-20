package com.sfdex.gmbioreactor.ui.viewmodel

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sfdex.gmbioreactor.data.model.AppSpoofConfig
import com.sfdex.gmbioreactor.data.model.DeviceProfile
import com.sfdex.gmbioreactor.data.repository.AppListRepository
import com.sfdex.gmbioreactor.data.repository.ConfigRepository
import com.sfdex.gmbioreactor.data.root.RootEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Filter type for application list.
 */
enum class AppFilterType(val label: String) {
    ALL("全部"),
    USER("用户应用"),
    SYSTEM("系统应用"),
    CONFIGURED("已配置")
}

/**
 * UI display representation of an application with its spoof status.
 */
data class AppDisplayItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isSpoofConfigured: Boolean = false,
    val isSpoofEnabled: Boolean = false,
    val spoofProfile: DeviceProfile? = null
)

/**
 * UI State for AppList Screen.
 */
data class AppListUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val filterType: AppFilterType = AppFilterType.ALL,
    val allApps: List<AppDisplayItem> = emptyList(),
    val displayedApps: List<AppDisplayItem> = emptyList(),
    val isRootAvailable: Boolean = false,
    val isZygiskModuleInstalled: Boolean = false,
    val statusMessage: String? = null,
    val isSuccessMessage: Boolean = true
)

class AppListViewModel(
    private val appListRepository: AppListRepository? = null,
    private val configRepository: ConfigRepository = ConfigRepository(),
    private val rootEngine: RootEngine = RootEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    /**
     * Loads installed applications, current root config, and module health status.
     */
    fun loadData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(ioDispatcher) {
            val rootAvailable = rootEngine.isRootAvailable()
            val zygiskInstalled = rootEngine.isZygiskModuleInstalled()
            val configs = configRepository.loadConfig()

            val installedApps = appListRepository?.getInstalledApps(includeIcons = true) ?: emptyList()

            // Map installed apps with their spoof configuration
            val installedPackagesSet = installedApps.map { it.packageName }.toSet()
            val appDisplayItems = mutableListOf<AppDisplayItem>()

            for (app in installedApps) {
                val cfg = configs[app.packageName]
                appDisplayItems.add(
                    AppDisplayItem(
                        packageName = app.packageName,
                        appName = app.appName,
                        isSystemApp = app.isSystemApp,
                        icon = app.icon,
                        isSpoofConfigured = cfg != null,
                        isSpoofEnabled = cfg?.enabled == true,
                        spoofProfile = cfg?.profile
                    )
                )
            }

            // Also include configured apps that might not be in installed list (e.g. test environments)
            for ((pkg, cfg) in configs) {
                if (!installedPackagesSet.contains(pkg)) {
                    appDisplayItems.add(
                        AppDisplayItem(
                            packageName = pkg,
                            appName = cfg.profile.name.ifBlank { pkg },
                            isSystemApp = false,
                            icon = null,
                            isSpoofConfigured = true,
                            isSpoofEnabled = cfg.enabled,
                            spoofProfile = cfg.profile
                        )
                    )
                }
            }

            // Sort: Configured apps first, then user apps, then alphabetical
            val sortedApps = appDisplayItems.sortedWith(
                compareByDescending<AppDisplayItem> { it.isSpoofConfigured }
                    .thenBy { it.isSystemApp }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
            )

            val currentSearch = _uiState.value.searchQuery
            val currentFilter = _uiState.value.filterType
            val filtered = filterDisplayApps(sortedApps, currentSearch, currentFilter)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    allApps = sortedApps,
                    displayedApps = filtered,
                    isRootAvailable = rootAvailable,
                    isZygiskModuleInstalled = zygiskInstalled
                )
            }
        }
    }

    /**
     * Updates the search query and filters displayed apps.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = filterDisplayApps(state.allApps, query, state.filterType)
            state.copy(
                searchQuery = query,
                displayedApps = filtered
            )
        }
    }

    /**
     * Updates the category filter and recalculates displayed apps.
     */
    fun setFilterType(type: AppFilterType) {
        _uiState.update { state ->
            val filtered = filterDisplayApps(state.allApps, state.searchQuery, type)
            state.copy(
                filterType = type,
                displayedApps = filtered
            )
        }
    }

    /**
     * Toggles whether spoofing is enabled for a target application.
     */
    fun toggleAppSpoof(packageName: String, enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val result = configRepository.toggleAppEnabled(packageName, enabled, forceStop = true)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        statusMessage = "已${if (enabled) "启用" else "暂停"} $packageName 的机型转基因",
                        isSuccessMessage = true
                    )
                }
                loadData()
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage = "操作失败: ${result.exceptionOrNull()?.message ?: "未知错误"}",
                        isSuccessMessage = false
                    )
                }
            }
        }
    }

    /**
     * Binds a device profile to a target package and saves config.
     */
    fun bindModelToApp(packageName: String, profile: DeviceProfile) {
        viewModelScope.launch(ioDispatcher) {
            val appConfig = AppSpoofConfig(
                packageName = packageName,
                enabled = true,
                profile = profile
            )
            val result = configRepository.saveAppConfig(appConfig, forceStop = true)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        statusMessage = "已为 $packageName 绑定机型 [${profile.name.ifBlank { profile.model }}]",
                        isSuccessMessage = true
                    )
                }
                loadData()
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage = "保存配置失败: ${result.exceptionOrNull()?.message ?: "未知错误"}",
                        isSuccessMessage = false
                    )
                }
            }
        }
    }

    /**
     * Removes spoof configuration for the target app.
     */
    fun removeAppSpoof(packageName: String) {
        viewModelScope.launch(ioDispatcher) {
            val result = configRepository.removeAppConfig(packageName, forceStop = true)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        statusMessage = "已取消 $packageName 的机型转基因",
                        isSuccessMessage = true
                    )
                }
                loadData()
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage = "取消配置失败: ${result.exceptionOrNull()?.message ?: "未知错误"}",
                        isSuccessMessage = false
                    )
                }
            }
        }
    }

    /**
     * Force stops a single application via Root am force-stop.
     */
    fun forceStopApp(packageName: String) {
        viewModelScope.launch(ioDispatcher) {
            val success = rootEngine.forceStopApp(packageName)
            if (success) {
                _uiState.update {
                    it.copy(
                        statusMessage = "已重启应用进程: $packageName",
                        isSuccessMessage = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage = "终止进程失败: 请检查 Root 权限",
                        isSuccessMessage = false
                    )
                }
            }
        }
    }

    /**
     * Force stops all applications that currently have spoofing enabled.
     */
    fun forceStopAllEnabled() {
        viewModelScope.launch(ioDispatcher) {
            val enabledPkgs = _uiState.value.allApps
                .filter { it.isSpoofEnabled }
                .map { it.packageName }

            if (enabledPkgs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        statusMessage = "当前没有启用转基因的应用",
                        isSuccessMessage = true
                    )
                }
                return@launch
            }

            val success = rootEngine.forceStopApps(enabledPkgs)

            if (success) {
                _uiState.update {
                    it.copy(
                        statusMessage = "已重启 ${enabledPkgs.size} 个转基因应用进程",
                        isSuccessMessage = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage = "批量重启失败: 请检查 Root 权限",
                        isSuccessMessage = false
                    )
                }
            }
        }
    }

    /**
     * Refreshes Root and Zygisk module statuses.
     */
    fun refreshRootStatus() {
        viewModelScope.launch(ioDispatcher) {
            val rootAvailable = rootEngine.isRootAvailable(forceCheck = true)
            val zygiskInstalled = rootEngine.isZygiskModuleInstalled()
            _uiState.update {
                it.copy(
                    isRootAvailable = rootAvailable,
                    isZygiskModuleInstalled = zygiskInstalled,
                    statusMessage = if (rootAvailable) "Root 权限正常" else "未检测到 Root 权限",
                    isSuccessMessage = rootAvailable
                )
            }
        }
    }

    /**
     * Exports current configurations as formatted JSON string.
     */
    fun exportConfigJson(onResult: (String) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            val configs = configRepository.loadConfig()
            val json = ConfigRepository.toJson(configs)
            onResult(json)
        }
    }

    /**
     * Imports configuration from JSON string and updates Root partition.
     */
    fun importConfigJson(jsonContent: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val parsed = ConfigRepository.parseJson(jsonContent)
                if (parsed.isEmpty() && jsonContent.trim() != "{}" && jsonContent.trim() != "{\n}") {
                    _uiState.update {
                        it.copy(
                            statusMessage = "导入失败: JSON 格式错误或未包含有效配置",
                            isSuccessMessage = false
                        )
                    }
                    return@launch
                }
                val result = configRepository.saveConfig(parsed)
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            statusMessage = "成功导入 ${parsed.size} 项配置并同步至 Root",
                            isSuccessMessage = true
                        )
                    }
                    loadData()
                } else {
                    _uiState.update {
                        it.copy(
                            statusMessage = "保存配置失败: ${result.exceptionOrNull()?.message ?: "未知错误"}",
                            isSuccessMessage = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage = "配置文件解析异常: ${e.message}",
                        isSuccessMessage = false
                    )
                }
            }
        }
    }

    /**
     * Clears the current snackbar/status message.
     */
    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun filterDisplayApps(
        apps: List<AppDisplayItem>,
        query: String,
        filterType: AppFilterType
    ): List<AppDisplayItem> {
        val trimmed = query.trim()
        return apps.filter { app ->
            val matchesFilter = when (filterType) {
                AppFilterType.ALL -> true
                AppFilterType.USER -> !app.isSystemApp
                AppFilterType.SYSTEM -> app.isSystemApp
                AppFilterType.CONFIGURED -> app.isSpoofConfigured
            }
            val matchesQuery = trimmed.isEmpty() ||
                    app.appName.contains(trimmed, ignoreCase = true) ||
                    app.packageName.contains(trimmed, ignoreCase = true) ||
                    (app.spoofProfile?.name?.contains(trimmed, ignoreCase = true) == true) ||
                    (app.spoofProfile?.model?.contains(trimmed, ignoreCase = true) == true) ||
                    (app.spoofProfile?.brand?.contains(trimmed, ignoreCase = true) == true)

            matchesFilter && matchesQuery
        }
    }
}
