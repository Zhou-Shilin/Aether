package com.zhousl.aether

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.defaultAppLanguage

object AetherLocaleManager {
    fun apply(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            LocaleList.forLanguageTags(language.languageTag)
    }

    fun applyIfChanged(context: Context, language: AppLanguage) {
        if (currentLanguage(context) == language) return
        apply(context, language)
    }

    fun currentLanguage(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return defaultAppLanguage()
        }
        val appLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        val locale = if (appLocales.isEmpty) {
            null
        } else {
            appLocales[0]
        }
        return locale?.let { defaultAppLanguage(it) } ?: defaultAppLanguage()
    }
}
