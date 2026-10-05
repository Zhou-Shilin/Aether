package com.zhousl.aether.ui

import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.AppThemeMode
import com.zhousl.aether.data.LlmCustomHeader
import com.zhousl.aether.data.LlmProviderConfig
import com.zhousl.aether.data.PiProviderEnvironmentVariable
import com.zhousl.aether.data.SharedAetherExtensionComponent
import com.zhousl.aether.data.SharedAetherExtensionSettingsCategory
import com.zhousl.aether.data.SharedAetherExtensionSettingsPage
import com.zhousl.aether.data.SharedAetherExtensionSnapshot
import com.zhousl.aether.data.SharedAetherExtensionSurface
import com.zhousl.aether.platform.PlatformCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeSettingsBridgeTest {
    @Test
    fun providerResultsCarryRequestAndSessionIdentityWithoutRequiringAuthentication() {
        val snapshot = Json.parseToJsonElement(buildNativeSettingsSnapshot(
            settings = AppSettings(),
            providerConfigs = emptyList(),
            installedSkills = emptyList(),
            extensionSnapshot = SharedAetherExtensionSnapshot(),
            capabilities = PlatformCapabilities.Ios,
            providerModels = mapOf("config" to listOf("model")),
            providerCompletedRequestId = "fetch-1",
            providerAuthSessionId = "login-2",
        )).jsonObject
        assertEquals("fetch-1", snapshot["providerCompletedRequestId"]!!.jsonPrimitive.content)
        assertEquals("login-2", snapshot["providerAuth"]!!.jsonObject["sessionId"]!!.jsonPrimitive.content)
        assertEquals("model", snapshot["providerModels"]!!.jsonObject["config"]!!.jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun snapshotUsesStableStorageValuesAndOnlyExportsSchemaDrivenExtensionSettings() {
        val snapshot = Json.parseToJsonElement(
            buildNativeSettingsSnapshot(
                settings = AppSettings(
                    language = AppLanguage.SimplifiedChinese,
                    themeMode = AppThemeMode.Dark,
                ),
                providerConfigs = emptyList(),
                installedSkills = emptyList(),
                extensionSnapshot = SharedAetherExtensionSnapshot(
                    settings = listOf(
                        SharedAetherExtensionSettingsPage(
                            id = "demo:settings",
                            localId = "settings",
                            extensionId = "demo",
                            extensionName = "Demo",
                            title = "Demo Settings",
                            subtitle = "Portable settings",
                            icon = "settings",
                            order = 0,
                            sections = listOf(
                                JsonObject(
                                    mapOf(
                                        "settings" to JsonArray(
                                            listOf(
                                                JsonObject(
                                                    mapOf(
                                                        "id" to JsonPrimitive("enabled"),
                                                        "type" to JsonPrimitive("toggle"),
                                                        "value" to JsonPrimitive(true),
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            ),
                            categories = listOf(
                                SharedAetherExtensionSettingsCategory(
                                    id = "editor",
                                    title = "Editor",
                                    subtitle = "Opened by an action",
                                    icon = "edit",
                                    order = 0,
                                    hidden = true,
                                    sections = emptyList(),
                                )
                            ),
                        )
                    ),
                    surfaces = listOf(
                        SharedAetherExtensionSurface("native", "demo", "Demo", "settings.hub", 0, null)
                    ),
                    components = listOf(
                        SharedAetherExtensionComponent("native", "demo", "Demo", "settings.screen", "replace", 0, null)
                    ),
                ),
                capabilities = PlatformCapabilities.Ios,
            )
        ).jsonObject

        assertEquals("zh-CN", snapshot["settings"]!!.jsonObject["language"]!!.jsonPrimitive.content)
        assertEquals("dark", snapshot["settings"]!!.jsonObject["themeMode"]!!.jsonPrimitive.content)
        assertEquals("demo:settings", snapshot["extensionSettings"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        val hiddenCategory = snapshot["extensionSettings"]!!.jsonArray.single().jsonObject["categories"]!!
            .jsonArray.single().jsonObject
        assertEquals("editor", hiddenCategory["id"]!!.jsonPrimitive.content)
        assertTrue(hiddenCategory["hidden"]!!.jsonPrimitive.content.toBoolean())
        assertFalse("surfaces" in snapshot)
        assertFalse("components" in snapshot)
    }

    @Test
    fun settingsPatchUpdatesOnlyProvidedValuesAndNormalizesNumbers() {
        val original = AppSettings(
            language = AppLanguage.English,
            themeMode = AppThemeMode.System,
            systemPrompt = "Keep me",
        )
        val updated = original.withNativeSettingsPatch(
            JsonObject(
                mapOf(
                    "language" to JsonPrimitive("fa"),
                    "themeMode" to JsonPrimitive("light"),
                    "llmInactivityReconnectTimeoutSeconds" to JsonPrimitive(1),
                    "keepTasksRunningInBackground" to JsonPrimitive(false),
                    "defaultChatModelKey" to JsonPrimitive("provider:model"),
                    "defaultTitleModelKey" to JsonPrimitive(""),
                    "defaultNamingModelKey" to JsonPrimitive("provider:naming"),
                    "defaultCompactingModelKey" to JsonPrimitive("provider:compact"),
                )
            )
        )

        assertEquals(AppLanguage.Persian, updated.language)
        assertEquals(AppThemeMode.Light, updated.themeMode)
        assertEquals(30, updated.llmInactivityReconnectTimeoutSeconds)
        assertFalse(updated.keepTasksRunningInBackground)
        assertEquals("Keep me", updated.systemPrompt)
        assertTrue(updated.autoCleanOldCommandHistory)
        assertEquals("provider:model", updated.defaultChatModelKey)
        assertEquals("", updated.defaultTitleModelKey)
        assertEquals("provider:naming", updated.defaultNamingModelKey)
        assertEquals("provider:compact", updated.defaultCompactingModelKey)
    }

    @Test
    fun snapshotExportsProviderCatalogModelsAndPreservesAdvancedProviderFields() {
        val provider = LlmProviderConfig(
            id = "provider-config",
            providerId = "work",
            name = "Work Provider",
            piProviderId = "openai-compatible",
            apiKey = "secret",
            baseUrl = "https://models.example/v1",
            providerEnvironmentVariables = listOf(PiProviderEnvironmentVariable("REGION", "test")),
            modelId = "model-a",
            manualModelIds = listOf("model-a"),
            userAgent = "Aether Test",
            customHeaders = listOf(LlmCustomHeader("X-Test", "value")),
            cachedModels = listOf("model-b"),
            enabledModelIds = listOf("model-a", "model-b"),
        )
        val snapshot = Json.parseToJsonElement(
            buildNativeSettingsSnapshot(
                settings = AppSettings(),
                providerConfigs = listOf(provider),
                installedSkills = emptyList(),
                extensionSnapshot = SharedAetherExtensionSnapshot(),
                capabilities = PlatformCapabilities.Ios,
            )
        ).jsonObject

        assertTrue(snapshot["providerCatalog"]!!.jsonArray.any {
            it.jsonObject["id"]!!.jsonPrimitive.content == "openai-compatible"
        })
        assertEquals(2, snapshot["modelOptions"]!!.jsonArray.size)
        val serialized = snapshot["providers"]!!.jsonArray.single().jsonObject
        assertEquals("Aether Test", serialized["userAgent"]!!.jsonPrimitive.content)
        assertEquals("REGION", serialized["providerEnvironmentVariables"]!!.jsonArray.single()
            .jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("X-Test", serialized["customHeaders"]!!.jsonArray.single()
            .jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun piCatalogParserReadsCurrentPackageCardShape() {
        val catalog = parseSharedPiPackageCatalog(
            """
            <article data-package-card data-package-name="pi-example"
                data-package-search="pi-example useful tools" data-package-types="extension skill"
                data-package-downloads="1234">
              <p class="packages-desc">Useful tools</p>
              <div class="packages-meta"><span>Aether</span><span>1.2K/mo</span></div>
              <div class="packages-badges"><span data-type="extension">extension</span></div>
              <div class="packages-links"><a href="https://www.npmjs.com/package/pi-example">npm</a></div>
              <a data-package-path="/packages/pi-example">Details</a>
              <button data-copy-text="pi install npm:pi-example">Copy</button>
            </article>
            """.trimIndent()
        )

        assertEquals(1, catalog.size)
        assertEquals("npm:pi-example", catalog.single().source)
        assertEquals("Useful tools", catalog.single().description)
        assertEquals(1234L, catalog.single().monthlyDownloads)
    }

    @Test
    fun installedPackageWithMissingResourceCountsIsStillRepresented() {
        val installed = parseSharedInstalledPackages(
            JsonObject(
                mapOf(
                    "packages" to JsonArray(
                        listOf(
                            JsonObject(
                                mapOf(
                                    "source" to JsonPrimitive("npm:preinstalled-extension"),
                                    "name" to JsonPrimitive("Preinstalled Extension"),
                                )
                            )
                        )
                    )
                )
            )
        )

        assertEquals(1, installed.size)
        assertEquals("npm:preinstalled-extension", installed.single().source)
        assertEquals(0, installed.single().extensionCount)
    }

    @Test
    fun snapshotExportsPiExtensionEnableStateAndMarkdownDetails() {
        val source = "npm:pi-example"
        val snapshot = Json.parseToJsonElement(
            buildNativeSettingsSnapshot(
                settings = AppSettings(),
                providerConfigs = emptyList(),
                installedSkills = emptyList(),
                extensionSnapshot = SharedAetherExtensionSnapshot(),
                capabilities = PlatformCapabilities.Ios,
                piExtensions = NativePiExtensionsState(
                    installed = listOf(
                        SharedInstalledExtension(
                            id = "package:$source",
                            source = source,
                            name = "Pi Example",
                            isEnabled = true,
                            kind = SharedExtensionInstallKind.Package,
                        )
                    ),
                    catalog = listOf(
                        SharedPiCatalogEntry(
                            name = "Pi Example",
                            source = source,
                            description = "Example extension",
                            author = "Aether",
                            monthlyDownloads = 42,
                            packageUrl = "https://pi.dev/packages/pi-example",
                            npmUrl = "https://npmjs.com/package/pi-example",
                            repositoryUrl = "https://github.com/example/pi-example",
                            types = listOf("extension", "skill"),
                            compatibilityIssue = null,
                        )
                    ),
                    details = SharedPiPackageDetails(
                        source = source,
                        name = "Pi Example",
                        description = "Example extension",
                        version = "1.0.0",
                        published = "2026-08-23",
                        downloads = "42",
                        author = "Aether",
                        license = "MIT",
                        size = "12 kB",
                        dependencies = "None",
                        types = listOf("extension", "skill"),
                        manifestJson = "{}",
                        readmeMarkdown = "# Pi Example\n\n- First feature",
                        npmUrl = "https://npmjs.com/package/pi-example",
                        repositoryUrl = "https://github.com/example/pi-example",
                        compatibilityIssue = null,
                    ),
                ),
            )
        ).jsonObject

        val extensions = snapshot["piExtensions"]!!.jsonObject
        assertTrue(extensions["installed"]!!.jsonArray.single().jsonObject["isEnabled"]!!
            .jsonPrimitive.content.toBoolean())
        assertEquals(source, extensions["details"]!!.jsonObject["source"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("extension", "skill"),
            extensions["details"]!!.jsonObject["types"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertContains(
            extensions["details"]!!.jsonObject["readmeMarkdown"]!!.jsonPrimitive.content,
            "# Pi Example",
        )
    }

    @Test
    fun snapshotExportsCompleteStatisticsAndAlpineState() {
        val statistics = com.zhousl.aether.data.SharedUsageStatisticsReport(
            totalTokens = 100,
            averageTurnTokens = 50,
            cachedInputTokens = 7,
            cacheWriteTokens = 42,
            allDailyTokenUsage = listOf(
                com.zhousl.aether.data.SharedDailyTokenUsage("2026-08-23", "Aug 23", "23", 100)
            ),
            recentSpeedSamples = listOf(
                com.zhousl.aether.data.SharedSpeedSample("Aug 23", "Aug 23", 12.5, 1L)
            ),
        )
        val snapshot = Json.parseToJsonElement(
            buildNativeSettingsSnapshot(
                settings = AppSettings(),
                providerConfigs = emptyList(),
                installedSkills = emptyList(),
                extensionSnapshot = SharedAetherExtensionSnapshot(),
                capabilities = PlatformCapabilities.Ios,
                statistics = statistics,
                alpine = NativeAlpineSettingsState(ready = true, issue = "ready"),
            )
        ).jsonObject

        assertEquals("50", snapshot["statistics"]!!.jsonObject["averageTurnTokens"]!!.jsonPrimitive.content)
        assertEquals("7", snapshot["statistics"]!!.jsonObject["cachedInputTokens"]!!.jsonPrimitive.content)
        assertEquals("42", snapshot["statistics"]!!.jsonObject["cacheWriteTokens"]!!.jsonPrimitive.content)
        assertEquals(1, snapshot["statistics"]!!.jsonObject["allDailyTokenUsage"]!!.jsonArray.size)
        assertEquals(1, snapshot["statistics"]!!.jsonObject["recentSpeedSamples"]!!.jsonArray.size)
        assertTrue(snapshot["alpine"]!!.jsonObject["ready"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(4, snapshot["alpine"]!!.jsonObject["profiles"]!!.jsonArray.size)
    }
}
