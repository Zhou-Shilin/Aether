package com.zhousl.aether.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedSkillManagerTest {
    @Test
    fun skillIdsMatchAndroidNameBasedRules() {
        assertEquals("document-review", buildSharedSkillId("Document Review"))
        assertEquals("pdf-json", buildSharedSkillId("PDF / JSON"))
        assertEquals("skill-fallback", buildSharedSkillId("文档", fallbackId = "fallback"))
    }

    @Test
    fun directoryEntriesAcceptNestedSkillResources() {
        validateSharedSkillDirectoryEntries(
            listOf(
                SharedSkillDirectoryEntry(
                    "nested/demo/SKILL.md",
                    "---\nname: Demo\ndescription: Demo Skill\n---".encodeToByteArray(),
                ),
                SharedSkillDirectoryEntry("nested/demo/references/guide.md", "Guide".encodeToByteArray()),
            )
        )
    }

    @Test
    fun directoryEntriesRejectEscapingPaths() {
        assertFailsWith<IllegalArgumentException> {
            validateSharedSkillDirectoryEntries(
                listOf(SharedSkillDirectoryEntry("../SKILL.md", ByteArray(0)))
            )
        }
    }

    @Test
    fun skillDocumentsRequireAndroidNameAndDescriptionFields() {
        assertFailsWith<IllegalArgumentException> {
            validateSharedSkillDocument("---\nname: Incomplete\n---")
        }
    }
    @Test
    fun resolvesGitHubRepositoryRoot() {
        assertEquals(
            SharedSkillRemoteSource("https://api.github.com/repos/openai/example/zipball"),
            resolveRemoteSkillSource("https://github.com/openai/example"),
        )
    }

    @Test
    fun preservesGitHubTreeSubpath() {
        assertEquals(
            SharedSkillRemoteSource(
                downloadUrl = "https://api.github.com/repos/openai/example/zipball/main",
                subpath = "skills/review",
            ),
            resolveRemoteSkillSource("https://github.com/openai/example/tree/main/skills/review"),
        )
    }

    @Test
    fun leavesDirectArchiveUrlUntouched() {
        val url = "https://example.com/aether-skill.zip"
        assertEquals(SharedSkillRemoteSource(url), resolveRemoteSkillSource(url))
    }

    @Test
    fun rejectsRemoteNonArchiveUrlsLikeAndroid() {
        assertFailsWith<IllegalArgumentException> {
            resolveRemoteSkillSource("https://example.com/skill")
        }
    }

    @Test
    fun acceptsHttpZipUrlsLikeAndroid() {
        val url = "http://example.com/aether-skill.zip"
        assertEquals(SharedSkillRemoteSource(url), resolveRemoteSkillSource(url))
    }

    @Test
    fun parsesDetailedSkillFrontMatter() {
        val metadata = parseSkillMetadata(
            """
            ---
            name: Review
            description: >
              Review a change carefully
              and report concrete findings.
            compatibility: iOS and Android
            license: MIT
            allowed-tools:
              - read_file
              - search_files
            ---
            Body
            """.trimIndent()
        )

        assertEquals("Review", metadata.name)
        assertEquals("Review a change carefully and report concrete findings.", metadata.description)
        assertEquals("iOS and Android", metadata.compatibility)
        assertEquals("MIT", metadata.license)
        assertEquals(listOf("read_file", "search_files"), metadata.allowedTools)
    }

    @Test
    fun quickActionLabelsMatchAndroidRules() {
        assertEquals("Create Skill", generateSharedQuickActionLabel("skill-creator"))
        assertEquals("PDF", generateSharedQuickActionLabel("Document helper", "Read and create PDF files"))
        assertEquals("GitHub", generateSharedQuickActionLabel("Repository assistant", "Work with GitHub"))
        assertEquals("Weather", generateSharedQuickActionLabel("weather-mcp-server"))
        assertEquals("Skill", generateSharedQuickActionLabel("skill"))
    }

    @Test
    fun implicitSkillMatchingUsesAndroidScoringAndExclusions() {
        val pdf = SharedInstalledSkill(
            id = "pdf",
            name = "PDF",
            description = "Read, create, and inspect PDF documents",
            guestPath = "/skills/pdf",
        )
        val github = SharedInstalledSkill(
            id = "github",
            name = "GitHub",
            description = "Review pull requests and repositories",
            guestPath = "/skills/github",
        )

        assertEquals(
            listOf("pdf"),
            findImplicitlyRelevantSharedSkills(
                skills = listOf(github, pdf),
                requestText = "Create a PDF report from these notes",
            ).map(SharedInstalledSkill::id),
        )
        assertTrue(
            findImplicitlyRelevantSharedSkills(
                skills = listOf(pdf),
                requestText = "Create a PDF report",
                excludedSkillIds = setOf("pdf"),
            ).isEmpty(),
        )
    }

    @Test
    fun activeSkillPromptMatchesAndroidContextShape() {
        val prompt = renderSharedActiveSkillPrompt(
            listOf(
                SharedActiveSkillContext(
                    skillId = "review",
                    name = "Review",
                    description = "Review changes",
                    skillRootPath = "/skills/review",
                    bodyMarkdown = "Inspect the change.",
                    resourceEntries = listOf(
                        SharedSkillResourceEntry("references/checklist.md", "reference"),
                    ),
                ),
            ),
        )

        assertTrue(prompt.startsWith("<active_skill name=\"Review\">"))
        assertTrue(prompt.contains("<instructions>\nInspect the change.\n</instructions>"))
        assertTrue(prompt.contains("references/checklist.md (reference)"))
    }
}
