package com.sfdex.gmbioreactor

import com.sfdex.gmbioreactor.data.model.AppSpoofConfig
import com.sfdex.gmbioreactor.data.model.BrandGroup
import com.sfdex.gmbioreactor.data.model.DeviceProfile
import com.sfdex.gmbioreactor.data.repository.AppItem
import com.sfdex.gmbioreactor.data.repository.AppListRepository
import com.sfdex.gmbioreactor.data.repository.ConfigRepository
import com.sfdex.gmbioreactor.data.repository.ModelRepository
import com.sfdex.gmbioreactor.ui.viewmodel.AppFilterType
import com.sfdex.gmbioreactor.ui.viewmodel.AppListViewModel
import com.sfdex.gmbioreactor.ui.viewmodel.ModelLibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleBrandGroups = listOf(
        BrandGroup(
            brandName = "Samsung",
            models = listOf(
                DeviceProfile(
                    name = "Samsung Galaxy S26 Ultra",
                    manufacturer = "samsung",
                    brand = "samsung",
                    model = "SM-S9480",
                    device = "s26ultra",
                    product = "s26ultrachn"
                ),
                DeviceProfile(
                    name = "Samsung Galaxy S25",
                    manufacturer = "samsung",
                    brand = "samsung",
                    model = "SM-S9310",
                    device = "s25",
                    product = "s25chn"
                )
            )
        ),
        BrandGroup(
            brandName = "Apple",
            models = listOf(
                DeviceProfile(
                    name = "iPad Pro 13 (M4)",
                    manufacturer = "Apple",
                    brand = "Apple",
                    model = "iPad16,4",
                    device = "J720AP",
                    product = "iPad16,4"
                )
            )
        ),
        BrandGroup(
            brandName = "ASUS",
            models = listOf(
                DeviceProfile(
                    name = "ROG Phone 9 Pro",
                    manufacturer = "asus",
                    brand = "asus",
                    model = "ASUS_AI2501A",
                    device = "AI2501",
                    product = "WW_AI2501"
                )
            )
        )
    )

    private val sampleAppList = listOf(
        AppItem("com.ruanmei.ithome", "IT之家", isSystemApp = false),
        AppItem("com.tencent.mm", "微信", isSystemApp = false),
        AppItem("com.miHoYo.Yuanshen", "原神", isSystemApp = false),
        AppItem("com.android.settings", "系统设置", isSystemApp = true),
        AppItem("com.google.android.gms", "Google Play 服务", isSystemApp = true)
    )

    private class FakeAppListRepo(private val apps: List<AppItem>) : AppListRepository(null) {
        override fun getInstalledApps(includeIcons: Boolean): List<AppItem> = apps
    }

    private fun createTestAppListViewModel(
        apps: List<AppItem> = sampleAppList,
        configRepo: ConfigRepository = ConfigRepository(ioDispatcher = testDispatcher)
    ): AppListViewModel {
        return AppListViewModel(
            appListRepository = FakeAppListRepo(apps),
            configRepository = configRepo,
            ioDispatcher = testDispatcher
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==========================================
    // AppListViewModel Tests
    // ==========================================

    @Test
    fun testAppListViewModel_InitialStateAndLoading() {
        val viewModel = createTestAppListViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(sampleAppList.size, state.allApps.size)
        assertEquals(sampleAppList.size, state.displayedApps.size)
        assertEquals(AppFilterType.ALL, state.filterType)
        assertEquals("", state.searchQuery)
    }

    @Test
    fun testAppListViewModel_SearchQueryFiltering() {
        val viewModel = createTestAppListViewModel()

        // Search by Chinese app name
        viewModel.setSearchQuery("微信")
        assertEquals(1, viewModel.uiState.value.displayedApps.size)
        assertEquals("com.tencent.mm", viewModel.uiState.value.displayedApps[0].packageName)

        // Search by package name substring
        viewModel.setSearchQuery("ruanmei")
        assertEquals(1, viewModel.uiState.value.displayedApps.size)
        assertEquals("com.ruanmei.ithome", viewModel.uiState.value.displayedApps[0].packageName)

        // Search query empty -> all apps
        viewModel.setSearchQuery("")
        assertEquals(sampleAppList.size, viewModel.uiState.value.displayedApps.size)
    }

    @Test
    fun testAppListViewModel_FilterTypeSwitching() {
        val viewModel = createTestAppListViewModel()

        // Filter User Apps
        viewModel.setFilterType(AppFilterType.USER)
        val userApps = viewModel.uiState.value.displayedApps
        assertEquals(3, userApps.size)
        assertTrue(userApps.all { !it.isSystemApp })

        // Filter System Apps
        viewModel.setFilterType(AppFilterType.SYSTEM)
        val systemApps = viewModel.uiState.value.displayedApps
        assertEquals(2, systemApps.size)
        assertTrue(systemApps.all { it.isSystemApp })

        // Filter Configured Apps (initially none)
        viewModel.setFilterType(AppFilterType.CONFIGURED)
        assertEquals(0, viewModel.uiState.value.displayedApps.size)

        // Back to ALL
        viewModel.setFilterType(AppFilterType.ALL)
        assertEquals(5, viewModel.uiState.value.displayedApps.size)
    }

    @Test
    fun testAppListViewModel_BindAndRemoveSpoofModel() {
        val viewModel = createTestAppListViewModel()

        val profile = DeviceProfile(
            name = "Samsung Galaxy S26 Ultra",
            manufacturer = "samsung",
            brand = "samsung",
            model = "SM-S9480",
            device = "s26ultra",
            product = "s26ultrachn"
        )

        // Bind profile
        viewModel.bindModelToApp("com.ruanmei.ithome", profile)

        val appItem = viewModel.uiState.value.allApps.find { it.packageName == "com.ruanmei.ithome" }
        assertNotNull(appItem)
        assertTrue("App should be configured", appItem!!.isSpoofConfigured)
        assertTrue("App spoofing should be enabled by default", appItem.isSpoofEnabled)
        assertEquals("SM-S9480", appItem.spoofProfile?.model)
        assertNotNull(viewModel.uiState.value.statusMessage)

        // Filter CONFIGURED now finds this app
        viewModel.setFilterType(AppFilterType.CONFIGURED)
        assertEquals(1, viewModel.uiState.value.displayedApps.size)
        assertEquals("com.ruanmei.ithome", viewModel.uiState.value.displayedApps[0].packageName)

        // Toggle enabled to false
        viewModel.toggleAppSpoof("com.ruanmei.ithome", false)
        val disabledApp = viewModel.uiState.value.allApps.find { it.packageName == "com.ruanmei.ithome" }
        assertNotNull(disabledApp)
        assertFalse("App spoofing should now be disabled", disabledApp!!.isSpoofEnabled)

        // Remove spoof configuration
        viewModel.removeAppSpoof("com.ruanmei.ithome")
        val removedApp = viewModel.uiState.value.allApps.find { it.packageName == "com.ruanmei.ithome" }
        assertNotNull(removedApp)
        assertFalse("App should no longer be configured", removedApp!!.isSpoofConfigured)
    }

    @Test
    fun testAppListViewModel_ForceStopAndStatusActions() {
        val viewModel = createTestAppListViewModel()

        viewModel.forceStopApp("com.tencent.mm")
        assertNotNull(viewModel.uiState.value.statusMessage)

        viewModel.forceStopAllEnabled()
        assertNotNull(viewModel.uiState.value.statusMessage)

        viewModel.refreshRootStatus()
        assertNotNull(viewModel.uiState.value.statusMessage)

        viewModel.clearStatusMessage()
        assertNull(viewModel.uiState.value.statusMessage)
    }

    // ==========================================
    // ModelLibraryViewModel Tests
    // ==========================================

    @Test
    fun testModelLibraryViewModel_PresetsAndBrandNames() {
        val modelRepo = ModelRepository(null)
        modelRepo.setBrandGroups(sampleBrandGroups)

        val viewModel = ModelLibraryViewModel(modelRepo)
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals(3, state.brandGroups.size)
        assertEquals(4, state.displayedProfiles.size) // 2 Samsung + 1 Apple + 1 ASUS
        assertTrue(state.brandNames.contains("全部"))
        assertTrue(state.brandNames.contains("Samsung"))
        assertTrue(state.brandNames.contains("Apple"))
        assertTrue(state.brandNames.contains("ASUS"))
    }

    @Test
    fun testModelLibraryViewModel_BrandFiltering() {
        val modelRepo = ModelRepository(null)
        modelRepo.setBrandGroups(sampleBrandGroups)
        val viewModel = ModelLibraryViewModel(modelRepo)

        // Select Samsung
        viewModel.selectBrand("Samsung")
        assertEquals(2, viewModel.uiState.value.displayedProfiles.size)
        assertTrue(viewModel.uiState.value.displayedProfiles.all { it.brand.equals("samsung", ignoreCase = true) })

        // Select Apple
        viewModel.selectBrand("Apple")
        assertEquals(1, viewModel.uiState.value.displayedProfiles.size)
        assertEquals("iPad Pro 13 (M4)", viewModel.uiState.value.displayedProfiles[0].name)

        // Reset to ALL
        viewModel.selectBrand("全部")
        assertEquals(4, viewModel.uiState.value.displayedProfiles.size)
    }

    @Test
    fun testModelLibraryViewModel_FullTextSearch() {
        val modelRepo = ModelRepository(null)
        modelRepo.setBrandGroups(sampleBrandGroups)
        val viewModel = ModelLibraryViewModel(modelRepo)

        // Search by model code
        viewModel.setSearchQuery("SM-S9480")
        assertEquals(1, viewModel.uiState.value.displayedProfiles.size)
        assertEquals("Samsung Galaxy S26 Ultra", viewModel.uiState.value.displayedProfiles[0].name)

        // Search by device name
        viewModel.setSearchQuery("AI2501")
        assertEquals(1, viewModel.uiState.value.displayedProfiles.size)
        assertEquals("ROG Phone 9 Pro", viewModel.uiState.value.displayedProfiles[0].name)

        // Search by manufacturer
        viewModel.setSearchQuery("apple")
        assertEquals(1, viewModel.uiState.value.displayedProfiles.size)
        assertEquals("iPad Pro 13 (M4)", viewModel.uiState.value.displayedProfiles[0].name)

        // Search non-existent
        viewModel.setSearchQuery("UnknownDeviceXYZ")
        assertEquals(0, viewModel.uiState.value.displayedProfiles.size)
    }

    @Test
    fun testModelLibraryViewModel_ProfileValidationAndEditFlow() {
        val modelRepo = ModelRepository(null)
        modelRepo.setBrandGroups(sampleBrandGroups)
        val viewModel = ModelLibraryViewModel(modelRepo)

        // Invalid profile should fail
        val invalidProfile = DeviceProfile(
            name = "Incomplete Profile",
            manufacturer = "",
            brand = "Brand",
            model = "Model",
            device = "",
            product = ""
        )
        val success = viewModel.saveCustomProfile(invalidProfile)
        assertFalse("Incomplete profile should not be saved", success)
        assertFalse(viewModel.uiState.value.isSuccessMessage)
        assertNotNull(viewModel.uiState.value.statusMessage)

        // Edit Flow State transitions
        viewModel.startCreateNewProfile()
        assertTrue(viewModel.uiState.value.isCreatingNew)
        assertNotNull(viewModel.uiState.value.editingProfile)

        val targetProfile = sampleBrandGroups[0].models[0]
        viewModel.startEditProfile(targetProfile)
        assertFalse(viewModel.uiState.value.isCreatingNew)
        assertEquals(targetProfile, viewModel.uiState.value.editingProfile)

        viewModel.dismissEditDialog()
        assertFalse(viewModel.uiState.value.isCreatingNew)
        assertNull(viewModel.uiState.value.editingProfile)
    }

    @Test
    fun testAppListViewModel_MergeConfiguredAppsNotInInstalledList() {
        val configRepo = ConfigRepository(ioDispatcher = testDispatcher)
        val testProfile = DeviceProfile(
            name = "Test Hidden App Profile",
            manufacturer = "test",
            brand = "test",
            model = "T1",
            device = "t1",
            product = "t1"
        )
        val initialConfigs = mapOf(
            "com.uninstalled.app" to AppSpoofConfig(
                packageName = "com.uninstalled.app",
                enabled = true,
                profile = testProfile
            )
        )
        configRepo.saveToCache(initialConfigs)

        val viewModel = createTestAppListViewModel(apps = sampleAppList, configRepo = configRepo)
        val uninstalledApp = viewModel.uiState.value.allApps.find { it.packageName == "com.uninstalled.app" }
        assertNotNull("Configured app not in installed list should be merged", uninstalledApp)
        assertTrue(uninstalledApp!!.isSpoofConfigured)
        assertTrue(uninstalledApp.isSpoofEnabled)
        assertEquals("T1", uninstalledApp.spoofProfile?.model)
    }

    @Test
    fun testAppListViewModel_ForceStopAllEnabledWithNoApps() {
        val viewModel = createTestAppListViewModel()
        // Initially no apps enabled
        viewModel.forceStopAllEnabled()
        val msg = viewModel.uiState.value.statusMessage
        assertNotNull(msg)
        assertTrue(msg!!.contains("当前没有启用转基因的应用"))
    }

    @Test
    fun testModelLibraryViewModel_CustomProfileCRUD() {
        val modelRepo = ModelRepository(null)
        modelRepo.setBrandGroups(sampleBrandGroups)
        val viewModel = ModelLibraryViewModel(modelRepo)

        val customProfile = DeviceProfile(
            name = "My Custom Flagship",
            manufacturer = "CustomCorp",
            brand = "CustomBrand",
            model = "CF-2026",
            device = "customflag",
            product = "customflag_chn"
        )

        // Save
        val saved = viewModel.saveCustomProfile(customProfile)
        assertTrue("Custom profile should save successfully", saved)
        assertTrue(viewModel.uiState.value.isSuccessMessage)

        // Filter by Custom
        viewModel.selectBrand(ModelLibraryViewModel.BRAND_CUSTOM)
        assertEquals(1, viewModel.uiState.value.displayedProfiles.size)
        assertEquals("My Custom Flagship", viewModel.uiState.value.displayedProfiles[0].name)

        // Delete
        val deleted = viewModel.deleteCustomProfile("My Custom Flagship")
        assertTrue("Custom profile should be deleted", deleted)
        assertEquals(0, viewModel.uiState.value.displayedProfiles.size)
    }
}
