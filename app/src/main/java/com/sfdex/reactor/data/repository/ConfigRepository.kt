package com.sfdex.reactor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.sfdex.reactor.data.model.AppSpoofConfig
import com.sfdex.reactor.data.model.DeviceProfile
import com.sfdex.reactor.data.root.RootEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class ConfigFileDto(
    @SerializedName("version")
    val version: Int = 1,
    @SerializedName("packages")
    val packages: Map<String, PackageEntryDto> = emptyMap()
)

internal data class PackageEntryDto(
    @SerializedName("enabled")
    val enabled: Boolean = true,
    @SerializedName("name")
    val name: String = "",
    @SerializedName("manufacturer")
    val manufacturer: String = "",
    @SerializedName("brand")
    val brand: String = "",
    @SerializedName("model")
    val model: String = "",
    @SerializedName("device")
    val device: String = "",
    @SerializedName("product")
    val product: String = ""
)

class ConfigRepository(
    private val context: Context? = null,
    private val rootEngine: RootEngine = RootEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private var inMemoryCache: Map<String, AppSpoofConfig>? = null

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        const val CONFIG_VERSION = 1
        const val PREFS_NAME = "gmbioreactor_config_cache"
        const val KEY_CACHED_CONFIG = "cached_config_json"

        private val gson: Gson by lazy {
            GsonBuilder().setPrettyPrinting().create()
        }

        fun toJson(configs: Map<String, AppSpoofConfig>): String {
            val dtoJson = ConfigFileDto(
                version = CONFIG_VERSION,
                packages = configs.mapValues { (_, appConfig) ->
                    PackageEntryDto(
                        enabled = appConfig.enabled,
                        name = appConfig.profile.name,
                        manufacturer = appConfig.profile.manufacturer,
                        brand = appConfig.profile.brand,
                        model = appConfig.profile.model,
                        device = appConfig.profile.device,
                        product = appConfig.profile.product
                    )
                }
            )
            return gson.toJson(dtoJson)
        }

        fun parseJson(jsonString: String?): Map<String, AppSpoofConfig> {
            if (jsonString.isNullOrBlank()) return emptyMap()
            return try {
                val dto = gson.fromJson(jsonString, ConfigFileDto::class.java) ?: return emptyMap()
                dto.packages.mapValues { (pkg, entry) ->
                    AppSpoofConfig(
                        packageName = pkg,
                        enabled = entry.enabled,
                        profile = DeviceProfile(
                            name = entry.name,
                            manufacturer = entry.manufacturer,
                            brand = entry.brand,
                            model = entry.model,
                            device = entry.device,
                            product = entry.product
                        )
                    )
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    suspend fun loadConfig(): Map<String, AppSpoofConfig> = withContext(ioDispatcher) {
        if (rootEngine.isRootAvailable()) {
            val rootContent = rootEngine.readConfigFile()
            if (rootContent != null) {
                val parsed = parseJson(rootContent)
                saveToCache(parsed)
                return@withContext parsed
            }
        }
        getCachedConfig()
    }

    suspend fun saveConfig(configs: Map<String, AppSpoofConfig>): Result<Unit> = withContext(ioDispatcher) {
        val json = toJson(configs)
        saveToCache(configs)

        if (rootEngine.isRootAvailable()) {
            val writeSuccess = rootEngine.writeConfigFile(json)
            if (writeSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("RootEngine failed to write configuration to ${RootEngine.CONFIG_FILE}"))
            }
        } else {
            Result.success(Unit)
        }
    }

    fun getCachedConfig(): Map<String, AppSpoofConfig> {
        val json = prefs?.getString(KEY_CACHED_CONFIG, null)
        if (json != null) {
            return parseJson(json)
        }
        return inMemoryCache ?: emptyMap()
    }

    fun saveToCache(configs: Map<String, AppSpoofConfig>): Boolean {
        inMemoryCache = configs
        val json = toJson(configs)
        return prefs?.edit()?.putString(KEY_CACHED_CONFIG, json)?.commit() ?: true
    }

    suspend fun getAppConfig(packageName: String): AppSpoofConfig? {
        val configs = loadConfig()
        return configs[packageName]
    }

    suspend fun saveAppConfig(appConfig: AppSpoofConfig, forceStop: Boolean = true): Result<Unit> {
        val configs = loadConfig().toMutableMap()
        configs[appConfig.packageName] = appConfig
        val result = saveConfig(configs)
        if (result.isSuccess && forceStop && appConfig.enabled && rootEngine.isRootAvailable()) {
            rootEngine.forceStopApp(appConfig.packageName)
        }
        return result
    }

    suspend fun removeAppConfig(packageName: String, forceStop: Boolean = true): Result<Unit> {
        val configs = loadConfig().toMutableMap()
        val removed = configs.remove(packageName) != null
        if (!removed) return Result.success(Unit)
        val result = saveConfig(configs)
        if (result.isSuccess && forceStop && rootEngine.isRootAvailable()) {
            rootEngine.forceStopApp(packageName)
        }
        return result
    }

    suspend fun toggleAppEnabled(packageName: String, enabled: Boolean, forceStop: Boolean = true): Result<Unit> {
        val configs = loadConfig().toMutableMap()
        val existing = configs[packageName]
            ?: return Result.failure(NoSuchElementException("App config not found for: $packageName"))
        configs[packageName] = existing.copy(enabled = enabled)
        val result = saveConfig(configs)
        if (result.isSuccess && forceStop && rootEngine.isRootAvailable()) {
            rootEngine.forceStopApp(packageName)
        }
        return result
    }
}
