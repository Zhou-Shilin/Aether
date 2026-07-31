package com.zhousl.aether.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhousl.aether.ui.theme.DarkAetherPalette
import com.zhousl.aether.ui.theme.LightAetherPalette
import com.zhousl.aether.ui.theme.updateAetherPalette
import com.zhousl.aether.data.AppThemeMode

private val lightColors = lightColorScheme(
    primary = LightAetherPalette.primary,
    onPrimary = LightAetherPalette.onPrimary,
    primaryContainer = LightAetherPalette.primaryContainer,
    onPrimaryContainer = LightAetherPalette.onPrimaryContainer,
    secondary = LightAetherPalette.secondary,
    onSecondary = LightAetherPalette.onSecondary,
    secondaryContainer = LightAetherPalette.secondaryContainer,
    onSecondaryContainer = LightAetherPalette.onSecondaryContainer,
    background = LightAetherPalette.background,
    surface = LightAetherPalette.surface,
    surfaceVariant = LightAetherPalette.surfaceVariant,
    onSurface = LightAetherPalette.onSurface,
    onSurfaceVariant = LightAetherPalette.onSurfaceVariant,
    tertiary = LightAetherPalette.tertiary,
    error = LightAetherPalette.error,
    outline = LightAetherPalette.outline,
)

private val darkColors = darkColorScheme(
    primary = DarkAetherPalette.primary,
    onPrimary = DarkAetherPalette.onPrimary,
    primaryContainer = DarkAetherPalette.primaryContainer,
    onPrimaryContainer = DarkAetherPalette.onPrimaryContainer,
    secondary = DarkAetherPalette.secondary,
    onSecondary = DarkAetherPalette.onSecondary,
    secondaryContainer = DarkAetherPalette.secondaryContainer,
    onSecondaryContainer = DarkAetherPalette.onSecondaryContainer,
    background = DarkAetherPalette.background,
    surface = DarkAetherPalette.surface,
    surfaceVariant = DarkAetherPalette.surfaceVariant,
    onSurface = DarkAetherPalette.onSurface,
    onSurfaceVariant = DarkAetherPalette.onSurfaceVariant,
    tertiary = DarkAetherPalette.tertiary,
    error = DarkAetherPalette.error,
    outline = DarkAetherPalette.outline,
)

private val typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.9).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 29.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 25.sp,
    ),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
internal fun SharedAetherTheme(
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> isSystemInDarkTheme()
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    SideEffect { updateAetherPalette(darkTheme) }
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        typography = typography,
        content = content,
    )
}
