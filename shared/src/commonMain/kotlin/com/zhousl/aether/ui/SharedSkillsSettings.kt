package com.zhousl.aether.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.data.SharedInstalledSkill
import com.zhousl.aether.data.SharedSkillDirectoryEntry
import com.zhousl.aether.data.SharedSkillManager
import com.zhousl.aether.data.generateSharedQuickActionLabel
import com.zhousl.aether.data.platformRandomUuid
import com.zhousl.aether.platform.PlatformServices
import com.zhousl.aether.runtime.MultiplatformLocalRuntime
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.action_collapse
import com.zhousl.aether.shared.resources.action_expand
import com.zhousl.aether.shared.resources.action_remove
import com.zhousl.aether.shared.resources.settings_add_skill
import com.zhousl.aether.shared.resources.settings_add_skill_description
import com.zhousl.aether.shared.resources.settings_agent_skills
import com.zhousl.aether.shared.resources.settings_any
import com.zhousl.aether.shared.resources.settings_choose_folder
import com.zhousl.aether.shared.resources.settings_choose_zip
import com.zhousl.aether.shared.resources.settings_import_skills_description
import com.zhousl.aether.shared.resources.settings_install_from_url
import com.zhousl.aether.shared.resources.settings_installing
import com.zhousl.aether.shared.resources.settings_no_skills_installed
import com.zhousl.aether.shared.resources.settings_remote_skill_url
import com.zhousl.aether.shared.resources.settings_remote_skill_url_description
import com.zhousl.aether.shared.resources.settings_select_skill_folder_description
import com.zhousl.aether.shared.resources.settings_select_skill_zip_description
import com.zhousl.aether.shared.resources.settings_skill_allowed_tools
import com.zhousl.aether.shared.resources.settings_skill_compatibility
import com.zhousl.aether.shared.resources.settings_skill_files
import com.zhousl.aether.shared.resources.settings_skill_id
import com.zhousl.aether.shared.resources.settings_skill_path
import com.zhousl.aether.shared.resources.settings_skill_source
import com.zhousl.aether.shared.resources.settings_skill_source_folder
import com.zhousl.aether.shared.resources.settings_skills_description
import com.zhousl.aether.shared.resources.message_install_skill_failed
import com.zhousl.aether.shared.resources.message_installed_skill
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class SharedSkillsPage { List, Add }

@Composable
internal fun SharedSkillsSettingsDetail(
    skillManager: SharedSkillManager,
    runtime: MultiplatformLocalRuntime,
    platformServices: PlatformServices,
    installedSkills: List<SharedInstalledSkill>,
    onSkillsChanged: (List<SharedInstalledSkill>) -> Unit,
    onTransientMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = SharedSkillsPage::valueOf,
        ),
    ) { mutableStateOf(SharedSkillsPage.List) }
    var busy by remember { mutableStateOf(false) }
    val installedNamePlaceholder = "{skill_name}"
    val installedMessageTemplate = stringResource(
        Res.string.message_installed_skill,
        installedNamePlaceholder,
    )
    val failurePlaceholder = "{skill_error}"
    val failedMessageTemplate = stringResource(
        Res.string.message_install_skill_failed,
        failurePlaceholder,
    )

    fun reportInstallFailure(failure: Throwable) {
        onTransientMessage(
            failedMessageTemplate.replace(
                failurePlaceholder,
                failure.message?.trim().takeUnless { it.isNullOrBlank() }
                    ?: failure::class.simpleName.orEmpty().ifBlank { "Unknown error." },
            ),
        )
    }

    fun runOperation(afterSuccess: () -> Unit = {}, operation: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val changed = operation()
                if (changed) {
                    onSkillsChanged(skillManager.list())
                    afterSuccess()
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
            } finally {
                busy = false
            }
        }
    }

    fun runInstall(
        afterSuccess: () -> Unit = {},
        operation: suspend () -> SharedInstalledSkill,
    ) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val installed = operation()
                onSkillsChanged(skillManager.list())
                onTransientMessage(
                    installedMessageTemplate.replace(installedNamePlaceholder, installed.name),
                )
                afterSuccess()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                reportInstallFailure(failure)
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SharedSettingsPageTransition(
            targetState = page,
            depth = { if (it == SharedSkillsPage.List) 0 else 1 },
            label = "skills_settings_page_transition",
        ) { currentPage ->
            when (currentPage) {
                SharedSkillsPage.List -> SharedSkillsListPage(
                skills = installedSkills,
                onToggle = { skill, enabled ->
                    runOperation { skillManager.setEnabled(skill.id, enabled); true }
                },
                onRemove = { skill -> runOperation { skillManager.remove(skill.id); true } },
                onAdd = { page = SharedSkillsPage.Add },
                onBack = onBack,
            )

                SharedSkillsPage.Add -> SharedAddSkillPage(
                busy = busy,
                onChooseFolder = {
                    scope.launch {
                        try {
                            val picked = platformServices.pickDirectory() ?: return@launch
                            runInstall {
                                skillManager.installDirectoryEntries(
                                    sourceLabel = picked.name,
                                    entries = picked.files.map { file ->
                                        SharedSkillDirectoryEntry(file.relativePath, file.bytes)
                                    },
                                )
                            }
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Throwable) {
                            reportInstallFailure(failure)
                        }
                    }
                },
                onChooseZip = {
                    scope.launch {
                        try {
                            val picked = platformServices.pickFile(false) ?: return@launch
                            runInstall(afterSuccess = { page = SharedSkillsPage.List }) {
                                val archive = "${runtime.workspaceRoot}/.skill-${platformRandomUuid()}.zip"
                                runtime.fileSystem.write(archive, picked.bytes)
                                try {
                                    skillManager.installArchive(archive, sourceLabel = picked.name)
                                } finally {
                                    withContext(NonCancellable) {
                                        runCatching { runtime.fileSystem.remove(archive) }
                                    }
                                }
                            }
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (failure: Throwable) {
                            reportInstallFailure(failure)
                        }
                    }
                },
                onInstallUrl = { url ->
                    runInstall(afterSuccess = { page = SharedSkillsPage.List }) {
                        skillManager.installRemote(url)
                    }
                },
                onBack = { page = SharedSkillsPage.List },
                )
            }
        }
    }
}

@Composable
private fun SharedSkillsListPage(
    skills: List<SharedInstalledSkill>,
    onToggle: (SharedInstalledSkill, Boolean) -> Unit,
    onRemove: (SharedInstalledSkill) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(Res.string.settings_skills_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            if (skills.isEmpty()) {
                SettingsCardGroup {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(Res.string.settings_no_skills_installed),
                            style = MaterialTheme.typography.titleMedium,
                            color = AetherOnSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.settings_import_skills_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SharedSettingsActionButton(
                            label = stringResource(Res.string.settings_add_skill),
                            onClick = onAdd,
                        )
                    }
                }
            } else {
                skills.forEach { skill ->
                    SharedSkillCard(
                        skill = skill,
                        onToggle = { onToggle(skill, it) },
                        onRemove = { onRemove(skill) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        SettingsTopBar(
            title = stringResource(Res.string.settings_agent_skills),
            onBack = onBack,
            trailingIcon = Icons.Rounded.Add,
            trailingContentDescription = stringResource(Res.string.settings_add_skill),
            onTrailingAction = onAdd,
        )
    }
}

@Composable
private fun SharedSkillCard(
    skill: SharedInstalledSkill,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by rememberSaveable(skill.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh).animateContentSize().padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    skill.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                if (skill.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                SharedActionPreviewPill(
                    skill.actionLabel.ifBlank {
                        generateSharedQuickActionLabel(skill.name, skill.description)
                    }
                )
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (expanded) Icons.Rounded.ArrowDropDown else Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = stringResource(
                        if (expanded) Res.string.action_collapse else Res.string.action_expand
                    ),
                    tint = AetherOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(Res.string.action_remove),
                    tint = Color(0xFFD25757),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SharedSettingsToggleRow(
            checked = skill.isEnabled,
            onCheckedChange = onToggle,
        )
        if (expanded) {
            Spacer(Modifier.height(28.dp))
            SharedSkillDetailLine(stringResource(Res.string.settings_skill_id), skill.id)
            SharedSkillDetailLine(stringResource(Res.string.settings_skill_files), skill.resourceCount.toString())
            SharedSkillDetailLine(
                stringResource(Res.string.settings_skill_allowed_tools),
                skill.allowedTools.ifEmpty { listOf(stringResource(Res.string.settings_any)) }.joinToString(", "),
            )
            if (skill.compatibility.isNotBlank()) {
                SharedSkillDetailLine(stringResource(Res.string.settings_skill_compatibility), skill.compatibility)
            }
            if (skill.source.isNotBlank()) {
                SharedSkillDetailLine(stringResource(Res.string.settings_skill_source), skill.source)
            }
            SharedSkillDetailLine(stringResource(Res.string.settings_skill_path), skill.guestPath)
        }
    }
}

@Composable
private fun SharedAddSkillPage(
    busy: Boolean,
    onChooseFolder: () -> Unit,
    onChooseZip: () -> Unit,
    onInstallUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var remoteUrl by rememberSaveable { mutableStateOf("") }
    val tabs = listOf(stringResource(Res.string.settings_skill_source_folder), "Zip", "URL")

    Box(Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(Res.string.settings_add_skill_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        onClick = { selectedTab = index },
                        selected = selectedTab == index,
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = AetherPrimary,
                            activeContentColor = Color.White,
                            inactiveContainerColor = AetherSurfaceHigh,
                            inactiveContentColor = AetherOnSurface,
                        ),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(20.dp))
            when (selectedTab) {
                0 -> SharedSkillPickerCard(
                    description = stringResource(Res.string.settings_select_skill_folder_description),
                    buttonLabel = stringResource(Res.string.settings_choose_folder),
                    onClick = onChooseFolder,
                )
                1 -> SharedSkillPickerCard(
                    description = stringResource(Res.string.settings_select_skill_zip_description),
                    buttonLabel = stringResource(Res.string.settings_choose_zip),
                    onClick = onChooseZip,
                )
                else -> {
                    SettingsCardGroup {
                        SharedInlineTextField(
                            label = stringResource(Res.string.settings_remote_skill_url),
                            value = remoteUrl,
                            onValueChange = { remoteUrl = it },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.settings_remote_skill_url_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = AetherOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    if (busy) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = AetherPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(Res.string.settings_installing), color = AetherOnSurfaceVariant)
                        }
                    } else {
                        SharedSettingsActionButton(
                            label = stringResource(Res.string.settings_install_from_url),
                            onClick = {
                                if (remoteUrl.isNotBlank()) onInstallUrl(remoteUrl)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        SettingsTopBar(
            title = stringResource(Res.string.settings_agent_skills),
            onBack = onBack,
        )
    }
}

@Composable
private fun SharedSkillPickerCard(
    description: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    SettingsCardGroup {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Folder, null, tint = AetherOnSurfaceVariant, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = AetherOnSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            SharedSettingsActionButton(label = buttonLabel, onClick = onClick)
        }
    }
}

@Composable
private fun SharedInlineTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().sharedSettingsBringIntoViewOnFocus(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = AetherOnSurface),
            cursorBrush = SolidColor(AetherPrimary),
            singleLine = true,
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AetherOnSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    field()
                }
            },
        )
    }
}

@Composable
private fun SharedSkillDetailLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AetherOnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = AetherOnSurface)
    }
}
