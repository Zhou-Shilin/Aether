package com.zhousl.aether

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.defaultAppLanguage

object AetherLocaleManager {
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
    }

    fun applyIfChanged(language: AppLanguage) {
        if (currentLanguage() == language) return
        apply(language)
    }

    fun currentLanguage(): AppLanguage {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) {
            null
        } else {
            appLocales[0]
        }
        return locale?.let { defaultAppLanguage(it) } ?: defaultAppLanguage()
    }
}