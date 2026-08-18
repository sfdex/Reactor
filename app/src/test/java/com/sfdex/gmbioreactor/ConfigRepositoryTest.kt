package com.sfdex.gmbioreactor

import com.google.gson.JsonParser
import com.sfdex.gmbioreactor.data.model.AppSpoofConfig
import com.sfdex.gmbioreactor.data.model.DeviceProfile
import com.sfdex.gmbioreactor.data.repository.AppItem
import com.sfdex.gmbioreactor.data.repository.AppListRepository
import com.sfdex.gmbioreactor.data.repository.ConfigRepository
import com.sfdex.gmbioreactor.data.root.SafeBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ConfigRepositoryTest {

    @Test
    fun testSerializeConfigToJsonMatchingSchema() {
        val sampleConfigs = mapOf(
            "com.ruanmei.ithome" to AppSpoofConfig(
                packageName = "com.ruanmei.ithome",
                enabled = true,
                profile = DeviceProfile(
                    name = "Samsung Galaxy S26 Ultra",
                    manufacturer = "samsung",
                    brand = "samsung",
                    model = "SM-S9480",
                    device = "s26ultra",
                    product = "s26ultrachn"
                )
            )
        )

        val json = ConfigRepository.toJson(sampleConfigs)
        assertNotNull(json)
        assertTrue(json.isNotBlank())

        val rootElement = JsonParser.parseString(json).asJsonObject
        assertTrue("JSON should have 'version' property", rootElement.has("version"))
        assertEquals(1, rootElement.get("version").asInt)

        assertTrue("JSON should have 'packages' property", rootElement.has("packages"))
        val packagesObj = rootElement.getAsJsonObject("packages")
        assertTrue("Packages should contain 'com.ruanmei.ithome'", packagesObj.has("com.ruanmei.ithome"))

        val itHomeObj = packagesObj.getAsJsonObject("com.ruanmei.ithome")
        assertTrue("Should have 'enabled' boolean", itHomeObj.has("enabled"))
        assertTrue(itHomeObj.get("enabled").asBoolean)
        assertEquals("Samsung Galaxy S26 Ultra", itHomeObj.get("name").asString)
        assertEquals("samsung", itHomeObj.get("manufacturer").asString)
        assertEquals("samsung", itHomeObj.get("brand").asString)
        assertEquals("SM-S9480", itHomeObj.get("model").asString)
        assertEquals("s26ultra", itHomeObj.get("device").asString)
        assertEquals("s26ultrachn", itHomeObj.get("product").asString)
    }

    @Test
    fun testParseConfigFromJsonSpec() {
        val specJson = """
        {
          "version": 1,
          "packages": {
            "com.ruanmei.ithome": {
              "enabled": true,
              "name": "Samsung Galaxy S26 Ultra",
              "manufacturer": "samsung",
              "brand": "samsung",
              "model": "SM-S9480",
              "device": "s26ultra",
              "product": "s26ultrachn"
            }
          }
        }
        """.trimIndent()

        val parsedMap = ConfigRepository.parseJson(specJson)
        assertEquals(1, parsedMap.size)
        assertTrue(parsedMap.containsKey("com.ruanmei.ithome"))

        val itHomeConfig = parsedMap["com.ruanmei.ithome"]!!
        assertEquals("com.ruanmei.ithome", itHomeConfig.packageName)
        assertTrue(itHomeConfig.enabled)
        assertEquals("Samsung Galaxy S26 Ultra", itHomeConfig.profile.name)
        assertEquals("samsung", itHomeConfig.profile.manufacturer)
        assertEquals("samsung", itHomeConfig.profile.brand)
        assertEquals("SM-S9480", itHomeConfig.profile.model)
        assertEquals("s26ultra", itHomeConfig.profile.device)
        assertEquals("s26ultrachn", itHomeConfig.profile.product)
    }

    @Test
    fun testRoundtripSerializationMultiplePackages() {
        val configs = mapOf(
            "com.ruanmei.ithome" to AppSpoofConfig(
                packageName = "com.ruanmei.ithome",
                enabled = true,
                profile = DeviceProfile(
                    name = "Samsung Galaxy S26 Ultra",
                    manufacturer = "samsung",
                    brand = "samsung",
                    model = "SM-S9480",
                    device = "s26ultra",
                    product = "s26ultrachn"
                )
            ),
            "com.tencent.tmgp.sgame" to AppSpoofConfig(
                packageName = "com.tencent.tmgp.sgame",
                enabled = false,
                profile = DeviceProfile(
                    name = "ROG Phone 9 Pro",
                    manufacturer = "asus",
                    brand = "asus",
                    model = "ASUS_AI2501A",
                    device = "AI2501",
                    product = "WW_AI2501"
                )
            ),
            "com.miHoYo.Yuanshen" to AppSpoofConfig(
                packageName = "com.miHoYo.Yuanshen",
                enabled = true,
                profile = DeviceProfile(
                    name = "iPad Pro 13 (M4)",
                    manufacturer = "Apple",
                    brand = "Apple",
                    model = "iPad16,4",
                    device = "J720AP",
                    product = "iPad16,4"
                )
            )
        )

        val json = ConfigRepository.toJson(configs)
        val deserialized = ConfigRepository.parseJson(json)

        assertEquals(configs.size, deserialized.size)
        for ((pkg, expected) in configs) {
            val actual = deserialized[pkg]
            assertNotNull("Package $pkg should exist in deserialized map", actual)
            assertEquals("Package name mismatch", expected.packageName, actual!!.packageName)
            assertEquals("Enabled mismatch for $pkg", expected.enabled, actual.enabled)
            assertEquals("Profile name mismatch for $pkg", expected.profile.name, actual.profile.name)
            assertEquals("Manufacturer mismatch for $pkg", expected.profile.manufacturer, actual.profile.manufacturer)
            assertEquals("Brand mismatch for $pkg", expected.profile.brand, actual.profile.brand)
            assertEquals("Model mismatch for $pkg", expected.profile.model, actual.profile.model)
            assertEquals("Device mismatch for $pkg", expected.profile.device, actual.profile.device)
            assertEquals("Product mismatch for $pkg", expected.profile.product, actual.profile.product)
        }
    }

    @Test
    fun testParseEmptyAndMalformedJson() {
        assertEquals(emptyMap<String, AppSpoofConfig>(), ConfigRepository.parseJson(null))
        assertEquals(emptyMap<String, AppSpoofConfig>(), ConfigRepository.parseJson(""))
        assertEquals(emptyMap<String, AppSpoofConfig>(), ConfigRepository.parseJson("   "))
        assertEquals(emptyMap<String, AppSpoofConfig>(), ConfigRepository.parseJson("{ malformed }"))
        assertEquals(emptyMap<String, AppSpoofConfig>(), ConfigRepository.parseJson("{}"))
        assertEquals(emptyMap<String, AppSpoofConfig>(), ConfigRepository.parseJson("{\"version\": 1}"))
        assertEquals(emptyMap<String, AppSpoofConfig>(), ConfigRepository.parseJson("{\"version\": 1, \"packages\": {}}"))
    }

    @Test
    fun testParsePartialFields() {
        val partialJson = """
        {
          "version": 1,
          "packages": {
            "com.example.test": {
              "model": "TestModel",
              "manufacturer": "TestManufacturer"
            }
          }
        }
        """.trimIndent()

        val result = ConfigRepository.parseJson(partialJson)
        assertEquals(1, result.size)
        val config = result["com.example.test"]
        assertNotNull(config)
        assertTrue("Enabled should default to true", config!!.enabled)
        assertEquals("TestModel", config.profile.model)
        assertEquals("TestManufacturer", config.profile.manufacturer)
        assertEquals("", config.profile.brand)
        assertEquals("", config.profile.device)
        assertEquals("", config.profile.product)
    }

    @Test
    fun testSafeBase64Encoding() {
        val sample = "{\"version\":1,\"packages\":{\"com.test\":{\"enabled\":true}}}"
        val encoded = SafeBase64.encode(sample.toByteArray(StandardCharsets.UTF_8))
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val javaBase64 = java.util.Base64.getEncoder().encodeToString(sample.toByteArray(StandardCharsets.UTF_8))
        assertEquals(javaBase64, encoded)
    }

    @Test
    fun testAppListFilterFunctionality() {
        val testApps = listOf(
            AppItem("com.ruanmei.ithome", "IT之家", isSystemApp = false),
            AppItem("com.tencent.mm", "微信", isSystemApp = false),
            AppItem("com.android.settings", "设置", isSystemApp = true),
            AppItem("com.google.android.gms", "Google Play 服务", isSystemApp = true)
        )

        // Filter user apps only
        val userOnly = testApps.filter { !it.isSystemApp }
        assertEquals(2, userOnly.size)

        // Query search
        val searchResults = testApps.filter { it.appName.contains("IT", ignoreCase = true) }
        assertEquals(1, searchResults.size)
        assertEquals("com.ruanmei.ithome", searchResults[0].packageName)

        val pkgSearchResults = testApps.filter { it.packageName.contains("tencent", ignoreCase = true) }
        assertEquals(1, pkgSearchResults.size)
        assertEquals("com.tencent.mm", pkgSearchResults[0].packageName)
    }
}
