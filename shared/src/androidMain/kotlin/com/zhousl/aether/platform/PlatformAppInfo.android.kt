package com.zhousl.aether.platform

import com.zhousl.aether.data.AppLanguage
import java.util.Locale

actual fun platformAppVersion(): String = "Android"

actual fun applyPlatformAppLanguage(language: AppLanguage) {
    Locale.setDefault(Locale.forLanguageTag(language.languageTag))
}

