package com.sfdex.gmbioreactor.ui.navigation

/**
 * Navigation 3 declarative routes for GMBioreactor.
 */
sealed interface AppRoute {
    /**
     * Main screen: Installed applications list, spoofing status toggles, and model binding.
     */
    data object AppList : AppRoute

    /**
     * MobileModels preset library and custom device profiles.
     */
    data object ModelLibrary : AppRoute

    /**
     * Model picker sub-route for selecting and binding a target device profile to an app.
     */
    data class ModelPicker(val packageName: String, val appName: String = "") : AppRoute

    /**
     * Root / Zygisk status, global actions, and module diagnosis.
     */
    data object Settings : AppRoute
}

enum class NavigationTab(
    val route: AppRoute,
    val label: String
) {
    APP_LIST(AppRoute.AppList, "应用管理"),
    MODEL_LIBRARY(AppRoute.ModelLibrary, "机型库"),
    SETTINGS(AppRoute.Settings, "设置")
}
