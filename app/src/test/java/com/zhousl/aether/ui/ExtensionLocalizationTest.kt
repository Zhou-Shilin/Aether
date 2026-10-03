package com.zhousl.aether.ui

import com.zhousl.aether.data.AppLanguage
import org.junit.Test
import org.junit.Assert.assertEquals

class ExtensionLocalizationTest {
    /** Verifies that translation affects only Russian and leaves unknown extension text unchanged. */
    @Test
    fun keepsOtherLanguagesAndUnknownTextIntact() {
        AppLanguage.entries.filter { it != AppLanguage.Russian }.forEach { language ->
            assertEquals("Web Access", extensionText("Web Access", language))
            assertEquals("Search workflow updated", extensionText("Search workflow updated", language))
        }
        assertEquals("Custom title", extensionText("Custom title", AppLanguage.Russian))
        assertEquals("Веб-доступ", extensionText("Web Access", AppLanguage.Russian))
        assertEquals("Процесс поиска обновлён", extensionText("Search workflow updated", AppLanguage.Russian))
    }

    /** Verifies complete Russian notification text while retaining the original diagnostic error detail. */
    @Test
    fun translatesNotificationWithoutChangingErrorDetail() {
        val message = "Web Access settings could not read the Pi config: ENOENT /data/pi/config.json"
        assertEquals(message, extensionText(message, AppLanguage.English))
        assertEquals("Настройки веб-доступа: не удалось прочитать конфигурацию Pi: ENOENT /data/pi/config.json", extensionText(message, AppLanguage.Russian))
    }

    /** Verifies the Russian builtin menu titles and preserves their titles in every other app language. */
    @Test
    fun translatesKnownMenuItemsOnlyInRussian() {
        assertEquals("Субагенты", extensionComposerMenuTitle("subagents", "Subagents", AppLanguage.Russian))
        assertEquals("Исследование в интернете", extensionComposerMenuTitle("research-web", "Research on the web", AppLanguage.Russian))
        AppLanguage.entries.filter { it != AppLanguage.Russian }.forEach { language ->
            assertEquals("Subagents", extensionComposerMenuTitle("subagents", "Subagents", language))
            assertEquals("Research on the web", extensionComposerMenuTitle("research-web", "Research on the web", language))
        }
    }

    /** Verifies that a custom composer entry keeps its own title when Russian is selected. */
    @Test
    fun preservesUnknownExtensionTitles() {
        assertEquals("Custom title", extensionComposerMenuTitle("custom", "Custom title", AppLanguage.Russian))
    }
}
