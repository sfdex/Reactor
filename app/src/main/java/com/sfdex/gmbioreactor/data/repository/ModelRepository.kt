package com.sfdex.gmbioreactor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sfdex.gmbioreactor.data.model.BrandGroup
import com.sfdex.gmbioreactor.data.model.DeviceProfile
import java.io.InputStreamReader

class ModelRepository(private val context: Context? = null) {

    private val gson = Gson()
    private var cachedBrandGroups: List<BrandGroup>? = null

    companion object {
        private const val PREFS_NAME = "gmbioreactor_custom_models"
        private const val KEY_CUSTOM_PROFILES = "custom_profiles_json"
        private const val ASSETS_MODELS_PATH = "models.json"

        fun parsePresetsJson(jsonString: String): List<BrandGroup> {
            val listType = object : TypeToken<List<BrandGroup>>() {}.type
            return try {
                Gson().fromJson(jsonString, listType) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadPresets(ctx: Context? = context): List<BrandGroup> {
        if (cachedBrandGroups != null) {
            return cachedBrandGroups!!
        }

        val targetContext = ctx ?: context ?: return emptyList()
        val brandGroups = try {
            targetContext.assets.open(ASSETS_MODELS_PATH).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val listType = object : TypeToken<List<BrandGroup>>() {}.type
                    gson.fromJson<List<BrandGroup>>(reader, listType) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        cachedBrandGroups = brandGroups
        return brandGroups
    }

    fun setBrandGroups(brandGroups: List<BrandGroup>) {
        this.cachedBrandGroups = brandGroups
    }

    fun getBrandGroups(): List<BrandGroup> {
        return cachedBrandGroups ?: loadPresets()
    }

    fun getAllPresets(): List<DeviceProfile> {
        return getBrandGroups().flatMap { it.models }
    }

    private var inMemoryCustomProfiles: MutableList<DeviceProfile>? = null

    fun getCustomProfiles(ctx: Context? = context): List<DeviceProfile> {
        val sharedPreferences = if (ctx != null && ctx != context) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } else {
            prefs
        }

        if (sharedPreferences == null) {
            return inMemoryCustomProfiles ?: emptyList()
        }

        val json = sharedPreferences.getString(KEY_CUSTOM_PROFILES, null) ?: return inMemoryCustomProfiles ?: emptyList()
        return try {
            val listType = object : TypeToken<List<DeviceProfile>>() {}.type
            gson.fromJson(json, listType) ?: (inMemoryCustomProfiles ?: emptyList())
        } catch (e: Exception) {
            inMemoryCustomProfiles ?: emptyList()
        }
    }

    fun saveCustomProfile(profile: DeviceProfile, ctx: Context? = context): Boolean {
        val currentList = getCustomProfiles(ctx).toMutableList()
        // Replace existing profile with same name or add new
        val index = currentList.indexOfFirst { it.name.equals(profile.name, ignoreCase = true) }
        if (index >= 0) {
            currentList[index] = profile
        } else {
            currentList.add(profile)
        }
        inMemoryCustomProfiles = currentList

        val sharedPreferences = if (ctx != null && ctx != context) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } else {
            prefs
        } ?: return true

        val json = gson.toJson(currentList)
        return sharedPreferences.edit().putString(KEY_CUSTOM_PROFILES, json).commit()
    }

    fun deleteCustomProfile(profileName: String, ctx: Context? = context): Boolean {
        val currentList = getCustomProfiles(ctx).toMutableList()
        val removed = currentList.removeAll { it.name.equals(profileName, ignoreCase = true) }
        if (!removed) return false
        inMemoryCustomProfiles = currentList

        val sharedPreferences = if (ctx != null && ctx != context) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } else {
            prefs
        } ?: return true

        val json = gson.toJson(currentList)
        return sharedPreferences.edit().putString(KEY_CUSTOM_PROFILES, json).commit()
    }

    fun getAllProfiles(ctx: Context? = context): List<DeviceProfile> {
        val presets = getAllPresets()
        val custom = getCustomProfiles(ctx)
        return custom + presets
    }

    fun searchModels(query: String, includeCustom: Boolean = true, ctx: Context? = context): List<DeviceProfile> {
        val trimmed = query.trim()
        val all = if (includeCustom) getAllProfiles(ctx) else getAllPresets()
        if (trimmed.isEmpty()) {
            return all
        }

        return all.filter { profile ->
            profile.name.contains(trimmed, ignoreCase = true) ||
                    profile.manufacturer.contains(trimmed, ignoreCase = true) ||
                    profile.brand.contains(trimmed, ignoreCase = true) ||
                    profile.model.contains(trimmed, ignoreCase = true) ||
                    profile.device.contains(trimmed, ignoreCase = true) ||
                    profile.product.contains(trimmed, ignoreCase = true)
        }
    }

    fun findProfileByName(name: String, ctx: Context? = context): DeviceProfile? {
        return getAllProfiles(ctx).find { it.name.equals(name, ignoreCase = true) }
    }
}
