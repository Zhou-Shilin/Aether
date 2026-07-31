package com.zhousl.aether.data

import com.zhousl.aether.data.chatdb.PersistedChatMessage
import com.zhousl.aether.data.chatdb.PersistedChatSession
import com.zhousl.aether.data.chatdb.PersistedChatUsage
import com.zhousl.aether.data.chatdb.PersistedAssistantResponseBlock
import com.zhousl.aether.data.chatdb.PersistedAssistantResponseBlockType
import com.zhousl.aether.data.chatdb.PersistedChatTool
import com.zhousl.aether.data.chatdb.PersistedReasoningTrace
import com.zhousl.aether.data.chatdb.serializePersistedChatSession
import com.zhousl.aether.data.chatdb.SharedDraftSessionId
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedAppDataArchiveTest {
    @Test
    fun androidDraftCurrentSessionIsPreservedWithHistoricalSessions() {
        val decoded = decodeSharedAppDataArchive(
            archiveJson(currentSessionId = "draft", includeSession = true),
        )

        assertEquals(SharedDraftSessionId, decoded.currentSessionId)
        assertEquals("draft", Json.parseToJsonElement(encodeSharedAppDataArchive(decoded))
            .jsonObject["currentSessionId"]?.jsonPrimitive?.content)
    }

    @Test
    fun legacySharedDraftCurrentSessionIsNormalizedForAndroid() {
        val decoded = decodeSharedAppDataArchive(
            archiveJson(currentSessionId = "aether-draft-session", includeSession = true),
        )

        assertEquals(SharedDraftSessionId, decoded.currentSessionId)
        assertEquals("draft", Json.parseToJsonElement(encodeSharedAppDataArchive(decoded))
            .jsonObject["currentSessionId"]?.jsonPrimitive?.content)
    }

    @Test
    fun emptyOrInvalidArchiveCurrentSessionFallsBackToDraft() {
        val empty = decodeSharedAppDataArchive(archiveJson(currentSessionId = "draft", includeSession = false))
        val invalid = decodeSharedAppDataArchive(archiveJson(currentSessionId = "missing", includeSession = true))

        assertEquals(SharedDraftSessionId, empty.currentSessionId)
        assertEquals(SharedDraftSessionId, invalid.currentSessionId)
    }

    @Test
    fun completeArchiveRoundTripsWithoutDroppingSensitiveOrHistoricalData() {
        val provider = LlmProviderConfig(
            id = "provider-config-1",
            providerId = "work_openai",
            name = "Work OpenAI",
            piProviderId = "openai",
            apiKey = "secret",
            baseUrl = "https://example.test/v1",
            modelId = "gpt-test",
        )
        val archive = SharedAppDataArchive(
            exportedAtMillis = 1234,
            settings = AppSettings(
                providerConfigId = provider.id,
                apiKey = "legacy-secret",
                tavilyApiKey = "tavily-secret",
                defaultSelectedSkillIds = listOf("review"),
            ),
            providerConfigs = Json.parseToJsonElement(
                serializeProviderConfigs(listOf(provider)),
            ).jsonArray,
            activeProviderConfigId = provider.id,
            sessions = listOf(
                PersistedChatSession(
                    id = "session-1",
                    title = "Test",
                    preview = "Answer",
                    messages = listOf(
                        PersistedChatMessage("user-1", "Question", fromUser = true, createdAtMillis = 10),
                        PersistedChatMessage(
                            id = "assistant-1",
                            text = "Answer",
                            fromUser = false,
                            usage = PersistedChatUsage(inputTokens = 5, outputTokens = 7, totalTokens = 12),
                            createdAtMillis = 20,
                            completedAtMillis = 30,
                        ),
                    ),
                    selectedSkillIds = listOf("review"),
                ),
            ),
            currentSessionId = "session-1",
            skillBundles = listOf(
                SharedSkillBundle(
                    id = "review",
                    name = "Review",
                    isEnabled = false,
                    files = listOf(
                        SharedSkillBundleFile(
                            path = "SKILL.md",
                            dataBase64 = Base64.encode(
                                "---\nname: Review\ndescription: Review changes\n---".encodeToByteArray(),
                            ),
                        ),
                    ),
                ),
            ),
            mcpServers = JsonArray(emptyList()),
        )

        val decoded = decodeSharedAppDataArchive(encodeSharedAppDataArchive(archive))

        assertEquals("secret", parseProviderConfigs(decoded.providerConfigs.toString()).single().apiKey)
        assertEquals("tavily-secret", decoded.settings.tavilyApiKey)
        assertEquals("Answer", decoded.sessions.single().messages.last().text)
        assertEquals(30L, decoded.sessions.single().messages.last().completedAtMillis)
        assertEquals(false, decoded.skillBundles.single().isEnabled)
        assertEquals("---\nname: Review\ndescription: Review changes\n---", Base64.decode(
            decoded.skillBundles.single().files.single().dataBase64,
        ).decodeToString())

        val exported = Json.parseToJsonElement(encodeSharedAppDataArchive(archive)).jsonObject
        assertFalse("activeProviderConfigId" in exported)
        assertEquals("app", exported["exportType"]?.toString()?.trim('"'))
        assertEquals(provider.id, exported["settings"]?.jsonObject?.get("providerConfigId")?.toString()?.trim('"'))
        assertEquals("system", exported["settings"]?.jsonObject?.get("themeMode")?.toString()?.trim('"'))
        val exportedMessage = exported["sessions"]!!.jsonArray.single().jsonObject["messages"]!!
            .jsonArray.last().jsonObject
        assertEquals("Agent", exportedMessage["author"]?.toString()?.trim('"'))
        assertTrue("usageStatistics" in exportedMessage)
        assertFalse("fromUser" in exportedMessage)
        assertFalse("usage" in exportedMessage)
    }

    private fun archiveJson(currentSessionId: String, includeSession: Boolean): String {
        val sessions = if (includeSession) {
            """[{"id":"session-1","title":"History","messages":[]}]"""
        } else {
            "[]"
        }
        return """
            {
              "schemaVersion": 2,
              "exportType": "app",
              "sessions": $sessions,
              "currentSessionId": "$currentSessionId"
            }
        """.trimIndent()
    }

    @Test
    fun importRejectsSkillPathTraversalBeforeWriting() {
        val archive = SharedAppDataArchive(
            exportedAtMillis = 1,
            settings = AppSettings(defaultSelectedSkillIds = listOf("bad")),
            providerConfigs = JsonArray(emptyList()),
            sessions = emptyList(),
            skillBundles = listOf(
                SharedSkillBundle(
                    id = "bad",
                    files = listOf(
                        SharedSkillBundleFile("SKILL.md", Base64.encode("ok".encodeToByteArray())),
                        SharedSkillBundleFile("../secret", Base64.encode("bad".encodeToByteArray())),
                    ),
                ),
            ),
            mcpServers = JsonArray(emptyList()),
        )

        assertFailsWith<IllegalArgumentException> { encodeSharedAppDataArchive(archive) }
    }

    @Test
    fun importMatchesAndroidPermissiveDefaultsForMissingSections() {
        val decoded = decodeSharedAppDataArchive("""{"schemaVersion":2,"exportType":"app"}""")

        assertEquals(emptyList(), decoded.sessions)
        assertEquals(emptyList(), decoded.skillBundles)
        assertEquals(AppThemeMode.System, decoded.settings.themeMode)
    }

    @Test
    fun malformedSettingsFieldFallsBackIndividuallyLikeAndroid() {
        val decoded = decodeSharedAppDataArchive(
            """
            {
              "schemaVersion": 2,
              "exportType": "app",
              "settings": {
                "piProviderId": "openai",
                "apiKey": "kept-secret",
                "modelId": "kept-model",
                "language": "zh-CN",
                "themeMode": "dark",
                "privacyPolicyAccepted": true,
                "llmInactivityReconnectTimeoutSeconds": {"invalid":true},
                "oldCommandHistoryRetentionHours": 999,
                "defaultSelectedSkillIds": ["review", 7, null, "review"],
                "providerEnvironmentVariables": [
                  {"name":"OPENAI_ORG_ID","value":"org"},
                  "invalid"
                ],
                "customHeaders": [
                  {"name":"X-Trace","value":"trace"},
                  {"value":"missing-name"}
                ],
                "alpinePackageProfiles": [
                  {"profileId":"core","installed":true,"installedAtMillis":123},
                  {"installed":true}
                ]
              }
            }
            """.trimIndent(),
        )

        val settings = decoded.settings
        assertEquals("openai", settings.piProviderId)
        assertEquals("kept-secret", settings.apiKey)
        assertEquals("kept-model", settings.modelId)
        assertEquals(AppLanguage.SimplifiedChinese, settings.language)
        assertEquals(AppThemeMode.Dark, settings.themeMode)
        assertTrue(settings.privacyPolicyAccepted)
        assertEquals(AppSettings().llmInactivityReconnectTimeoutSeconds, settings.llmInactivityReconnectTimeoutSeconds)
        assertEquals(168, settings.oldCommandHistoryRetentionHours)
        assertEquals(listOf("review", "7", "review"), settings.defaultSelectedSkillIds)
        assertEquals("OPENAI_ORG_ID", settings.providerEnvironmentVariables.single().name)
        assertEquals("X-Trace", settings.customHeaders.single().name)
        assertEquals(123L, settings.alpinePackageProfiles.getValue("core").installedAtMillis)
    }

    @Test
    fun malformedChatMessageCreatesAndroidRecoverySessionInsteadOfDroppingHistory() {
        val decoded = decodeSharedAppDataArchive(
            """
            {
              "schemaVersion": 2,
              "exportType": "app",
              "sessions": [{
                "id": "damaged",
                "title": "Original title",
                "agentModeEnabled": false,
                "messages": [
                  {"id":"valid","author":"User","text":"Keep me"},
                  17
                ]
              }]
            }
            """.trimIndent(),
        )

        val recovery = decoded.sessions.single()
        assertTrue(recovery.id.startsWith("corrupt-chat-state-"))
        assertEquals("Chat storage needs recovery", recovery.title)
        assertEquals("Stored chat data could not be parsed.", recovery.preview)
        assertTrue(recovery.hasCustomTitle)
        assertTrue(recovery.messages.single().text.contains("recovery placeholder"))
        assertTrue(recovery.messages.single().providerPayloadJson.contains("Original title"))
    }

    @Test
    fun androidSessionShapeRoundTripsOrderedAssistantWork() {
        val session = PersistedChatSession(
            id = "session",
            title = "Work",
            preview = "Done",
            messages = listOf(
                PersistedChatMessage("user", "Do it", fromUser = true, createdAtMillis = 10),
                PersistedChatMessage(
                    id = "assistant",
                    text = "Done",
                    fromUser = false,
                    responseBlocks = listOf(
                        PersistedAssistantResponseBlock(
                            id = "reasoning",
                            type = PersistedAssistantResponseBlockType.Reasoning,
                            reasoningTrace = PersistedReasoningTrace(
                                id = "reasoning",
                                rawText = "Think",
                                startedAtMillis = 20,
                                completedAtMillis = 25,
                            ),
                        ),
                        PersistedAssistantResponseBlock(
                            id = "tool",
                            type = PersistedAssistantResponseBlockType.ToolGroup,
                            tools = listOf(
                                PersistedChatTool(
                                    id = "tool-call",
                                    name = "read",
                                    summary = "Read",
                                    outputJson = "{\"ok\":true}",
                                    completedAtMillis = 26,
                                ),
                            ),
                        ),
                        PersistedAssistantResponseBlock(
                            id = "text",
                            type = PersistedAssistantResponseBlockType.Text,
                            text = "Done",
                        ),
                    ),
                    usage = PersistedChatUsage(inputTokens = 3, outputTokens = 2, totalTokens = 5),
                    createdAtMillis = 20,
                    completedAtMillis = 30,
                    responseDurationMillis = 4,
                    firstTokenLatencyMillis = 6,
                    responseGroupId = "agent-group-20",
                ),
            ),
            selectedSkillIds = listOf("review"),
            activeSkills = listOf(
                SharedActiveSkillContext(
                    skillId = "review",
                    name = "Review",
                    description = "Review changes",
                    skillRootPath = "/skills/review",
                    bodyMarkdown = "Inspect the change.",
                ),
            ),
        )
        val archive = SharedAppDataArchive(
            exportedAtMillis = 40,
            settings = AppSettings(),
            providerConfigs = JsonArray(emptyList()),
            sessions = listOf(session),
            skillBundles = emptyList(),
            mcpServers = JsonArray(emptyList()),
        )

        val encoded = encodeSharedAppDataArchive(archive)
        val root = Json.parseToJsonElement(encoded).jsonObject
        val messages = root["sessions"]!!.jsonArray.single().jsonObject["messages"]!!.jsonArray
        assertEquals(4, messages.size)
        val assistantMessages = messages.drop(1).map { it.jsonObject }
        assertTrue(assistantMessages.all { "author" in it && "responseBlocks" !in it })
        assertTrue(assistantMessages.all {
            it["responseGroupId"]?.toString()?.trim('"') == "agent-group-20"
        })
        assertEquals(1, assistantMessages.count { "reasoningTrace" in it })
        assertEquals(1, assistantMessages.count { it["toolInvocations"]?.jsonArray?.isNotEmpty() == true })
        assertEquals(1, assistantMessages.count { "usageStatistics" in it })

        val restoredSession = decodeSharedAppDataArchive(encoded).sessions.single()
        val restored = restoredSession.messages.last()
        assertEquals("Done", restored.text)
        assertEquals(3, restored.responseBlocks.size)
        assertEquals(5, restored.usage?.totalTokens)
        assertEquals("Inspect the change.", restoredSession.activeSkills.single().bodyMarkdown)

        val singleExportText = serializePersistedChatSession(session)
        assertTrue(singleExportText.contains("\n  \"schemaVersion\": 1,"))
        assertTrue(singleExportText.contains("\n  \"session\": {"))
        val singleExport = Json.parseToJsonElement(singleExportText).jsonObject
        assertEquals("1", singleExport["schemaVersion"].toString())
        val activeSkillsJson = singleExport["session"]!!.jsonObject["activeSkillsJson"]!!
            .jsonPrimitive.content
        assertEquals("review", Json.parseToJsonElement(activeSkillsJson).jsonArray.single()
            .jsonObject["skillId"]?.jsonPrimitive?.content)
        assertEquals("Agent", singleExport["session"]!!.jsonObject["messages"]!!
            .jsonArray.last().jsonObject["author"]?.toString()?.trim('"'))
    }

    @Test
    fun importSkipsMalformedItemsIndividuallyLikeAndroid() {
        val skillMarkdown = Base64.encode(
            "---\nname: Review\ndescription: Review changes\n---".encodeToByteArray(),
        )
        val incompleteSkillMarkdown = Base64.encode("---\nname: Incomplete\n---".encodeToByteArray())
        val decoded = decodeSharedAppDataArchive(
            """
            {
              "schemaVersion": 2,
              "exportType": "app",
              "providerConfigs": [17, {"name":"Recovered","piProviderId":"openai"}],
              "skillBundles": [
                {"id":"review","files":[{"path":"SKILL.md","dataBase64":"$skillMarkdown"}]},
                {"id":"incomplete","files":[{"path":"SKILL.md","dataBase64":"$incompleteSkillMarkdown"}]},
                {"id":"escaped","files":[{"path":"../secret","dataBase64":"$skillMarkdown"}]},
                "invalid"
              ],
              "mcpServers": [
                {"id":"missing-transport","displayName":"Ignored"},
                {
                  "displayName":"Docs",
                  "transport":{"type":"streamable_http","url":"https://example.test/mcp"},
                  "createdAtMillis":123,
                  "updatedAtMillis":456
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("review"), decoded.skillBundles.map { it.id })
        val reencoded = Json.parseToJsonElement(encodeSharedAppDataArchive(decoded)).jsonObject
        assertEquals(1, reencoded["providerConfigs"]!!.jsonArray.size)
        val mcp = reencoded["mcpServers"]!!.jsonArray.single().jsonObject
        assertEquals("mcp-1", mcp["id"]?.toString()?.trim('"'))
        assertEquals("123", mcp["createdAtMillis"].toString())
        assertEquals("456", mcp["updatedAtMillis"].toString())
    }

    @Test
    fun androidUsageToolTimingAndRecursiveBranchesRoundTripWithoutInventingFields() {
        val decoded = decodeSharedAppDataArchive(
            """
            {
              "schemaVersion": 2,
              "exportType": "app",
              "sessions": [{
                "id": "session",
                "messages": [
                  {
                    "id": "root-user",
                    "author": "User",
                    "text": "Root",
                    "createdAtMillis": 10,
                    "branchGroup": {
                      "selectedIndex": 1,
                      "branches": [
                        [
                          {"id":"first-user","author":"User","text":"First","createdAtMillis":11},
                          {"id":"first-tail","author":"Agent","text":"First tail","responseGroupId":"first-group","createdAtMillis":12}
                        ],
                        [
                          {
                            "id": "second-user",
                            "author": "User",
                            "text": "Second",
                            "createdAtMillis": 13,
                            "branchGroup": {
                              "selectedIndex": 0,
                              "branches": [
                                [{"id":"nested-a","author":"User","text":"Nested A","createdAtMillis":14}],
                                [{"id":"nested-b","author":"User","text":"Nested B","createdAtMillis":15}]
                              ]
                            }
                          },
                          {"id":"second-tail","author":"Agent","text":"Second tail","responseGroupId":"second-group","createdAtMillis":16}
                        ]
                      ]
                    }
                  },
                  {
                    "id": "assistant",
                    "author": "Agent",
                    "text": "Done",
                    "responseGroupId": "agent-group-20",
                    "createdAtMillis": 20,
                    "providerPayloadJson": "{\"provider\":\"raw\"}",
                    "toolInvocations": [{
                      "id": "tool",
                      "toolName": "read",
                      "argumentsJson": "{\"path\":\"notes.txt\"}",
                      "outputJson": "{\"ok\":true}",
                      "isRunning": true,
                      "startedAtUptimeMillis": 321,
                      "completedAtUptimeMillis": 654,
                      "startedAtMillis": 21,
                      "completedAtMillis": 22
                    }],
                    "usageStatistics": {
                      "totalTokens": 42,
                      "requestCount": 3,
                      "tokenUsageSource": "provider",
                      "startedAtMillis": 20,
                      "firstTokenAtMillis": 20,
                      "completedAtMillis": 30
                    }
                  }
                ]
              }]
            }
            """.trimIndent(),
        )

        val session = decoded.sessions.single()
        val rootUser = session.messages.first()
        assertEquals(1, rootUser.selectedUserBranchIndex)
        assertEquals("First tail", rootUser.userBranches[0].last().text)
        assertEquals("Second tail", rootUser.userBranches[1].last().text)
        assertEquals("Nested A", rootUser.userBranches[1].first().userBranches[0].single().text)

        val assistant = session.messages.last()
        val usage = assistant.usage!!
        assertEquals(42, usage.totalTokens)
        assertTrue(usage.totalTokensAvailable)
        assertFalse(usage.inputTokensAvailable)
        assertFalse(usage.outputTokensAvailable)
        assertFalse(usage.reasoningTokensAvailable)
        assertFalse(usage.cachedInputTokensAvailable)
        assertEquals(3, usage.requestCount)
        assertEquals(0L, assistant.firstTokenLatencyMillis)
        assertEquals("{\"provider\":\"raw\"}", assistant.providerPayloadJson)
        assertEquals(321L, assistant.tools.single().startedAtUptimeMillis)
        assertEquals(654L, assistant.tools.single().completedAtUptimeMillis)

        val reencoded = Json.parseToJsonElement(encodeSharedAppDataArchive(decoded)).jsonObject
        val messages = reencoded["sessions"]!!.jsonArray.single().jsonObject["messages"]!!.jsonArray
        val exportedAssistant = messages.last().jsonObject
        val exportedUsage = exportedAssistant["usageStatistics"]!!.jsonObject
        assertEquals("42", exportedUsage["totalTokens"].toString())
        assertEquals("3", exportedUsage["requestCount"].toString())
        assertFalse("inputTokens" in exportedUsage)
        assertFalse("outputTokens" in exportedUsage)
        assertFalse("reasoningTokens" in exportedUsage)
        assertFalse("cachedInputTokens" in exportedUsage)
        assertEquals("20", exportedUsage["firstTokenAtMillis"].toString())
        assertEquals(
            "{\"provider\":\"raw\"}",
            exportedAssistant["providerPayloadJson"]?.jsonPrimitive?.content,
        )
        val exportedTool = exportedAssistant["toolInvocations"]!!.jsonArray.single().jsonObject
        assertEquals("321", exportedTool["startedAtUptimeMillis"].toString())
        assertEquals("654", exportedTool["completedAtUptimeMillis"].toString())

        val restoredAgain = decodeSharedAppDataArchive(reencoded.toString()).sessions.single()
        assertEquals("Nested B", restoredAgain.messages.first().userBranches[1].first()
            .userBranches[1].single().text)
        assertEquals("Second tail", restoredAgain.messages.first().userBranches[1].last().text)
    }
}
