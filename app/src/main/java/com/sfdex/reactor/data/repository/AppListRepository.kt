package com.sfdex.reactor.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
)

open class AppListRepository(private val context: Context? = null) {

    /**
     * Retrieves all installed applications, excluding GMBioreactor itself.
     * Sorts user applications first, then alphabetically by app name.
     */
    open fun getInstalledApps(includeIcons: Boolean = true): List<AppItem> {
        val targetContext = context ?: return emptyList()
        val pm = targetContext.packageManager
        val selfPackage = targetContext.packageName

        val installedApps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        return installedApps
            .filter { it.packageName != selfPackage }
            .map { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    appInfo.packageName
                }
                val icon = if (includeIcons) {
                    try {
                        appInfo.loadIcon(pm)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                AppItem(
                    packageName = appInfo.packageName,
                    appName = appName,
                    isSystemApp = isSystem,
                    icon = icon
                )
            }
            .sortedWith(
                compareBy<AppItem> { it.isSystemApp }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
            )
    }

    /**
     * Asynchronously retrieves installed applications on Dispatchers.IO.
     */
    suspend fun getInstalledAppsAsync(includeIcons: Boolean = true): List<AppItem> =
        withContext(Dispatchers.IO) {
            getInstalledApps(includeIcons)
        }

    /**
     * Retrieves icon for a specific package name.
     */
    open fun getAppIcon(packageName: String): Drawable? {
        val targetContext = context ?: return null
        return try {
            val pm = targetContext.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadIcon(pm)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Filters list of apps by search query and system app filter.
     */
    fun filterApps(
        apps: List<AppItem>,
        query: String,
        showSystemApps: Boolean = true
    ): List<AppItem> {
        val trimmed = query.trim()
        return apps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
                    (trimmed.isEmpty() ||
                            app.appName.contains(trimmed, ignoreCase = true) ||
                            app.packageName.contains(trimmed, ignoreCase = true))
        }
    }
}
