package com.sfdex.gmbioreactor

import com.sfdex.gmbioreactor.data.model.DeviceProfile
import com.sfdex.gmbioreactor.data.repository.ModelRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelRepositoryTest {

    private lateinit var repository: ModelRepository
    private var modelsJsonString: String = ""

    @Before
    fun setUp() {
        repository = ModelRepository()

        // Read models.json from assets directory
        val assetsFile = File("src/main/assets/models.json")
        if (assetsFile.exists()) {
            modelsJsonString = assetsFile.readText()
        } else {
            val stream = javaClass.classLoader?.getResourceAsStream("models.json")
            if (stream != null) {
                modelsJsonString = stream.bufferedReader().use { it.readText() }
            }
        }
        assertTrue("models.json content should not be empty", modelsJsonString.isNotBlank())
        val brandGroups = ModelRepository.parsePresetsJson(modelsJsonString)
        repository.setBrandGroups(brandGroups)
    }

    @Test
    fun testParsePresetsContainsExpectedBrands() {
        val brandGroups = repository.getBrandGroups()
        assertTrue("Brand groups should not be empty", brandGroups.isNotEmpty())

        val brandNames = brandGroups.map { it.brandName }
        val requiredBrands = listOf(
            "Samsung",
            "Xiaomi",
            "Redmi",
            "Huawei",
            "HONOR",
            "vivo",
            "iQOO",
            "OPPO",
            "OnePlus",
            "Google Pixel",
            "Apple",
            "Meizu",
            "Sony"
        )

        for (brand in requiredBrands) {
            assertTrue("Expected brand '$brand' in dataset, found: $brandNames", brandNames.contains(brand))
        }
    }

    @Test
    fun testAllModelsHaveValidProperties() {
        val allPresets = repository.getAllPresets()
        assertTrue("Preset count should be at least 50", allPresets.size >= 50)

        for (profile in allPresets) {
            assertTrue("Profile name should not be blank: ${profile.name}", profile.name.isNotBlank())
            assertTrue("Manufacturer should not be blank for ${profile.name}", profile.manufacturer.isNotBlank())
            assertTrue("Brand should not be blank for ${profile.name}", profile.brand.isNotBlank())
            assertTrue("Model should not be blank for ${profile.name}", profile.model.isNotBlank())
            assertTrue("Device should not be blank for ${profile.name}", profile.device.isNotBlank())
            assertTrue("Product should not be blank for ${profile.name}", profile.product.isNotBlank())
            assertTrue("Profile isValid() should return true for ${profile.name}", profile.isValid())
        }
    }

    @Test
    fun testSearchModelsByBrand() {
        val results = repository.searchModels("Xiaomi", includeCustom = false)
        assertTrue("Should find multiple Xiaomi models", results.isNotEmpty())
        for (profile in results) {
            val matches = profile.name.contains("Xiaomi", ignoreCase = true) ||
                    profile.brand.contains("Xiaomi", ignoreCase = true) ||
                    profile.manufacturer.contains("Xiaomi", ignoreCase = true)
            assertTrue("Result should match query: ${profile.name}", matches)
        }
    }

    @Test
    fun testSearchModelsByModelCode() {
        val s24UltraResults = repository.searchModels("SM-S9280", includeCustom = false)
        assertEquals("Should find exactly Galaxy S24 Ultra by model code", 1, s24UltraResults.size)
        assertEquals("Samsung Galaxy S24 Ultra", s24UltraResults[0].name)
        assertEquals("Samsung", s24UltraResults[0].manufacturer)
        assertEquals("samsung", s24UltraResults[0].brand)
        assertEquals("SM-S9280", s24UltraResults[0].model)
        assertEquals("e3q", s24UltraResults[0].device)
        assertEquals("e3qzh", s24UltraResults[0].product)
    }

    @Test
    fun testSearchModelsByDeviceCodename() {
        val xiaomi14Ultra = repository.searchModels("aurora", includeCustom = false)
        assertTrue("Should find Xiaomi 14 Ultra by codename aurora", xiaomi14Ultra.isNotEmpty())
        assertEquals("Xiaomi 14 Ultra", xiaomi14Ultra[0].name)

        val pixel9ProXL = repository.searchModels("komodo", includeCustom = false)
        assertTrue("Should find Pixel 9 Pro XL by codename komodo", pixel9ProXL.isNotEmpty())
        assertEquals("Google Pixel 9 Pro XL", pixel9ProXL[0].name)
    }

    @Test
    fun testSearchWithEmptyQueryReturnsAll() {
        val all = repository.getAllPresets()
        val emptySearchResults = repository.searchModels("", includeCustom = false)
        assertEquals(all.size, emptySearchResults.size)
    }

    @Test
    fun testFindProfileByName() {
        val profile = repository.findProfileByName("HUAWEI Mate 70 Pro")
        assertNotNull("Should find profile for HUAWEI Mate 70 Pro", profile)
        assertEquals("HUAWEI", profile?.manufacturer)
        assertEquals("HBN-AL00", profile?.model)
    }
}
