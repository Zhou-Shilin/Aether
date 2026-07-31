package com.zhousl.aether.platform

import com.zhousl.aether.data.AppLanguage

expect fun platformAppVersion(): String

/**
 * Updates the locale source consumed by Compose Resources. The app content is
 * keyed by the selected language, so the new environment is read immediately.
 */
expect fun applyPlatformAppLanguage(language: AppLanguage)

