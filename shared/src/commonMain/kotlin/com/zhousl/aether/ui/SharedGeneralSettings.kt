package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AppSettings
import com.zhousl.aether.data.AppThemeMode
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.*
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedGeneralSettingsDetail(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCardGroup {
                SharedSelectionDropdownField(
                    label = stringResource(Res.string.settings_language),
                    supportingText = stringResource(Res.string.settings_language_description),
                    selectedLabel = sharedLanguageDisplayName(settings.language),
                    options = AppLanguage.entries.map { option ->
                        SharedSelectionOption(
                            title = sharedLanguageDisplayName(option),
                            subtitle = when (option) {
                                AppLanguage.English -> stringResource(Res.string.settings_language_english_interface)
                                AppLanguage.SimplifiedChinese ->
                                    stringResource(Res.string.settings_language_simplified_chinese_interface)
                            },
                            selected = option == settings.language,
                            onClick = {
                                if (option != settings.language) {
                                    onSave(settings.copy(language = option))
                                }
                            },
                        )
                    },
                )
            }
            SettingsCardGroup {
                SharedSelectionDropdownField(
                    label = stringResource(Res.string.settings_theme),
                    supportingText = stringResource(Res.string.settings_theme_description),
                    selectedLabel = sharedThemeDisplayName(settings.themeMode),
                    options = AppThemeMode.entries.map { option ->
                        SharedSelectionOption(
                            title = sharedThemeDisplayName(option),
                            subtitle = when (option) {
                                AppThemeMode.System -> stringResource(Res.string.settings_system_theme_subtitle)
                                AppThemeMode.Light -> stringResource(Res.string.settings_light_theme_subtitle)
                                AppThemeMode.Dark -> stringResource(Res.string.settings_dark_theme_subtitle)
                            },
                            selected = option == settings.themeMode,
                            onClick = {
                                if (option != settings.themeMode) {
                                    onSave(settings.copy(themeMode = option))
                                }
                            },
                        )
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        SettingsTopBar(title = stringResource(Res.string.settings_general), onBack = onBack)
    }
}

@Composable
private fun sharedLanguageDisplayName(language: AppLanguage): String = when (language) {
    AppLanguage.English -> stringResource(Res.string.language_english)
    AppLanguage.SimplifiedChinese -> stringResource(Res.string.language_simplified_chinese)
}

@Composable
private fun sharedThemeDisplayName(themeMode: AppThemeMode): String = when (themeMode) {
    AppThemeMode.System -> stringResource(Res.string.theme_system)
    AppThemeMode.Light -> stringResource(Res.string.theme_light)
    AppThemeMode.Dark -> stringResource(Res.string.theme_dark)
}

private data class SharedSelectionOption(
    val title: String,
    val subtitle: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun SharedSelectionDropdownField(
    label: String,
    supportingText: String,
    selectedLabel: String,
    options: List<SharedSelectionOption>,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = AetherOnSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = AetherOnSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AetherBackground)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(selectedLabel, style = MaterialTheme.typography.bodyLarge, color = AetherOnSurface)
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = label,
                tint = AetherOnSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AetherSurface),
        ) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AetherSurface)
                        .clickable {
                            expanded = false
                            option.onClick()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.title, color = AetherOnSurface)
                        Text(
                            option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                        )
                    }
                    if (option.selected) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = AetherPrimary)
                    }
                }
            }
        }
    }
}
