package com.zhousl.aether.platform

import com.zhousl.aether.data.AppLanguage
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

private var appliedLanguageTag: String? = null

actual fun platformAppVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: "1.0"

actual fun applyPlatformAppLanguage(language: AppLanguage) {
    if (appliedLanguageTag == language.languageTag) return
    NSUserDefaults.standardUserDefaults.setObject(
        listOf(language.languageTag),
        forKey = "AppleLanguages",
    )
    NSUserDefaults.standardUserDefaults.synchronize()
    appliedLanguageTag = language.languageTag
}
