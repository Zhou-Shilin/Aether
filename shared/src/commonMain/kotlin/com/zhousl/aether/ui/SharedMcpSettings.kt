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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.zhousl.aether.data.generateSharedQuickActionLabel
import com.zhousl.aether.data.pi.SharedMcpInspection
import com.zhousl.aether.data.pi.SharedMcpManager
import com.zhousl.aether.data.pi.SharedMcpServerConfig
import com.zhousl.aether.data.pi.SharedMcpTransport
import com.zhousl.aether.data.platformCurrentTimeMillis
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.action_collapse
import com.zhousl.aether.shared.resources.action_edit
import com.zhousl.aether.shared.resources.action_expand
import com.zhousl.aether.shared.resources.action_remove
import com.zhousl.aether.shared.resources.settings_add_http_server
import com.zhousl.aether.shared.resources.settings_add_mcp_server_description
import com.zhousl.aether.shared.resources.settings_add_mcp_server_page_description
import com.zhousl.aether.shared.resources.settings_add_server
import com.zhousl.aether.shared.resources.settings_add_stdio_server
import com.zhousl.aether.shared.resources.settings_arguments
import com.zhousl.aether.shared.resources.settings_command
import com.zhousl.aether.shared.resources.settings_connect_timeout
import com.zhousl.aether.shared.resources.settings_default_runtime
import com.zhousl.aether.shared.resources.settings_default_runtime_help
import com.zhousl.aether.shared.resources.settings_environment
import com.zhousl.aether.shared.resources.settings_headers
import com.zhousl.aether.shared.resources.settings_mcp_prompts
import com.zhousl.aether.shared.resources.settings_mcp_resources
import com.zhousl.aether.shared.resources.settings_mcp_servers
import com.zhousl.aether.shared.resources.settings_mcp_servers_description
import com.zhousl.aether.shared.resources.settings_mcp_tools
import com.zhousl.aether.shared.resources.settings_no_mcp_servers
import com.zhousl.aether.shared.resources.settings_optional_environment_hint
import com.zhousl.aether.shared.resources.settings_optional_headers_hint
import com.zhousl.aether.shared.resources.settings_quick_action
import com.zhousl.aether.shared.resources.settings_request_timeout
import com.zhousl.aether.shared.resources.settings_runtime_alpine_stdio_subtitle
import com.zhousl.aether.shared.resources.settings_runtime_environment
import com.zhousl.aether.shared.resources.settings_runtime_environment_default_help
import com.zhousl.aether.shared.resources.settings_save_http_server
import com.zhousl.aether.shared.resources.settings_save_stdio_server
import com.zhousl.aether.shared.resources.settings_server_id
import com.zhousl.aether.shared.resources.settings_server_name
import com.zhousl.aether.shared.resources.settings_server_url
import com.zhousl.aether.shared.resources.settings_stdio
import com.zhousl.aether.shared.resources.settings_transport
import com.zhousl.aether.shared.resources.settings_update_mcp_server_description
import com.zhousl.aether.shared.resources.settings_working_dir
import com.zhousl.aether.shared.resources.settings_working_directory
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource

private enum class SharedMcpPage { List, Editor }

@Composable
internal fun SharedMcpSettingsDetail(
    manager: SharedMcpManager,
    servers: List<SharedMcpServerConfig>,
    activeServerIds: Set<String>,
    onServersChanged: (List<SharedMcpServerConfig>) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = SharedMcpPage::valueOf,
        ),
    ) { mutableStateOf(SharedMcpPage.List) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var persistBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    val toolsLabel = stringResource(Res.string.settings_mcp_tools)
    val resourcesLabel = stringResource(Res.string.settings_mcp_resources)
    val promptsLabel = stringResource(Res.string.settings_mcp_prompts)

    fun persist(updated: List<SharedMcpServerConfig>, afterSuccess: () -> Unit = {}): Boolean {
        if (persistBusy) return false
        persistBusy = true
        status = ""
        statusIsError = false
        scope.launch {
            try {
                manager.saveServers(updated)
                onServersChanged(updated)
                afterSuccess()
                runCatching {
                    manager.refreshBindings(
                        updated.filter { it.enabled && it.id in activeServerIds },
                    )
                }
                    .onFailure {
                        if (it is CancellationException) throw it
                        status = it.message.orEmpty().ifBlank { "Unable to refresh MCP tools." }
                        statusIsError = true
                    }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                status = failure.message.orEmpty()
                statusIsError = true
            } finally {
                persistBusy = false
            }
        }
        return true
    }

    fun inspect(server: SharedMcpServerConfig, operation: SharedMcpInspection) {
        status = ""
        statusIsError = false
        scope.launch {
            try {
                status = formatMcpInspection(
                    response = manager.inspectServer(server.id, operation),
                    operation = operation,
                    title = when (operation) {
                        SharedMcpInspection.Tools -> toolsLabel
                        SharedMcpInspection.Resources -> resourcesLabel
                        SharedMcpInspection.Prompts -> promptsLabel
                    },
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                status = "Test failed: ${failure.message ?: "Unknown MCP error."}"
                statusIsError = true
            }
        }
    }

    SharedSettingsPageTransition(
        targetState = page,
        depth = { if (it == SharedMcpPage.List) 0 else 1 },
        label = "mcp_settings_page_transition",
    ) { currentPage ->
        when (currentPage) {
            SharedMcpPage.List -> SharedMcpListPage(
            servers = servers,
            status = status,
            onToggle = { server, enabled ->
                persist(servers.map { if (it.id == server.id) it.copy(enabled = enabled) else it })
            },
            onRemove = { server -> persist(servers.filterNot { it.id == server.id }) },
            onInspect = ::inspect,
            onEdit = { server ->
                editingId = server.id
                status = ""
                page = SharedMcpPage.Editor
            },
            onAdd = {
                editingId = null
                status = ""
                page = SharedMcpPage.Editor
            },
            onBack = onBack,
        )

            SharedMcpPage.Editor -> SharedMcpEditorPage(
            existing = editingId?.let { id -> servers.firstOrNull { it.id == id } },
            error = status.takeIf { statusIsError }.orEmpty(),
            onSave = { server ->
                val existing = servers.firstOrNull { it.id == server.id }
                val started = persist(
                    updated = if (existing == null) servers + server
                    else servers.map { if (it.id == server.id) server else it },
                )
                if (started) {
                    editingId = null
                    page = SharedMcpPage.List
                }
            },
            onBack = {
                status = ""
                editingId = null
                page = SharedMcpPage.List
            },
            )
        }
    }
}

@Composable
private fun SharedMcpListPage(
    servers: List<SharedMcpServerConfig>,
    status: String,
    onToggle: (SharedMcpServerConfig, Boolean) -> Unit,
    onRemove: (SharedMcpServerConfig) -> Unit,
    onInspect: (SharedMcpServerConfig, SharedMcpInspection) -> Unit,
    onEdit: (SharedMcpServerConfig) -> Unit,
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
                stringResource(Res.string.settings_mcp_servers_description),
                style = MaterialTheme.typography.bodySmall,
                color = AetherOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            if (servers.isEmpty()) {
                SettingsCardGroup {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(Res.string.settings_no_mcp_servers),
                            style = MaterialTheme.typography.titleMedium,
                            color = AetherOnSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(Res.string.settings_add_mcp_server_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AetherOnSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SharedSettingsActionButton(
                            label = stringResource(Res.string.settings_add_server),
                            onClick = onAdd,
                        )
                    }
                }
            } else {
                servers.forEach { server ->
                    SharedMcpServerCard(
                        server = server,
                        onToggle = { onToggle(server, it) },
                        onEdit = { onEdit(server) },
                        onRemove = { onRemove(server) },
                        onInspect = { onInspect(server, it) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (status.isNotBlank()) {
                    SettingsCardGroup {
                        Text(
                            status,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        SettingsTopBar(
            title = stringResource(Res.string.settings_mcp_servers),
            onBack = onBack,
            trailingIcon = Icons.Rounded.Add,
            trailingContentDescription = stringResource(Res.string.settings_add_server),
            onTrailingAction = onAdd,
        )
    }
}

@Composable
private fun SharedMcpServerCard(
    server: SharedMcpServerConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onInspect: (SharedMcpInspection) -> Unit,
) {
    var expanded by rememberSaveable(server.id) { mutableStateOf(false) }
    val endpoint = if (server.transport == SharedMcpTransport.Http) server.url
    else listOf(server.command, *server.arguments.toTypedArray()).joinToString(" ")
    val quickAction = server.actionLabel.ifBlank {
        generateSharedQuickActionLabel(server.name, endpoint)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(AetherSurfaceHigh).animateContentSize().padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AetherOnSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (server.transport == SharedMcpTransport.Http) "STREAMABLE_HTTP" else "STDIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = AetherOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SharedActionPreviewPill(quickAction)
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
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(Res.string.action_edit),
                    tint = AetherOnSurface,
                    modifier = Modifier.size(18.dp),
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
            checked = server.enabled,
            onCheckedChange = onToggle,
        )
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_mcp_tools),
                    onClick = { onInspect(SharedMcpInspection.Tools) },
                    modifier = Modifier.weight(1f),
                )
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_mcp_resources),
                    onClick = { onInspect(SharedMcpInspection.Resources) },
                    modifier = Modifier.weight(1f),
                )
                SharedSettingsSubtleActionButton(
                    label = stringResource(Res.string.settings_mcp_prompts),
                    onClick = { onInspect(SharedMcpInspection.Prompts) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            SharedMcpDetailLine(stringResource(Res.string.settings_server_id), server.id)
            SharedMcpDetailLine(stringResource(Res.string.settings_quick_action), quickAction)
            SharedMcpDetailLine(
                stringResource(Res.string.settings_transport),
                if (server.transport == SharedMcpTransport.Http) "STREAMABLE_HTTP" else "STDIO",
            )
            if (server.transport == SharedMcpTransport.Http) {
                SharedMcpDetailLine("URL", server.url)
                SharedMcpDetailLine(stringResource(Res.string.settings_headers), server.headers.size.toString())
            } else {
                SharedMcpDetailLine(stringResource(Res.string.settings_command), server.command)
                if (server.workingDirectory.isNotBlank()) {
                    SharedMcpDetailLine(
                        stringResource(Res.string.settings_working_dir),
                        server.workingDirectory,
                    )
                }
                SharedMcpDetailLine(stringResource(Res.string.settings_environment), server.environment.size.toString())
            }
            SharedMcpDetailLine(
                stringResource(Res.string.settings_connect_timeout),
                "${server.connectTimeoutMillis} ms",
            )
            SharedMcpDetailLine(
                stringResource(Res.string.settings_request_timeout),
                "${server.requestTimeoutMillis} ms",
            )
        }
    }
}

@Composable
private fun SharedMcpEditorPage(
    existing: SharedMcpServerConfig?,
    error: String,
    onSave: (SharedMcpServerConfig) -> Unit,
    onBack: () -> Unit,
) {
    val existingHttp = existing?.takeIf { it.transport == SharedMcpTransport.Http }
    val existingStdio = existing?.takeIf { it.transport == SharedMcpTransport.Stdio }
    var selectedTab by rememberSaveable(existing?.id) {
        mutableIntStateOf(if (existingStdio != null) 1 else 0)
    }
    var httpName by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var httpUrl by rememberSaveable(existing?.id) { mutableStateOf(existingHttp?.url.orEmpty()) }
    var httpHeaders by rememberSaveable(existing?.id) {
        mutableStateOf(existingHttp?.headers?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty())
    }
    var stdioName by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var stdioCommand by rememberSaveable(existing?.id) { mutableStateOf(existingStdio?.command.orEmpty()) }
    var stdioArguments by rememberSaveable(existing?.id) {
        mutableStateOf(existingStdio?.arguments?.joinToString(" ").orEmpty())
    }
    var stdioWorkingDirectory by rememberSaveable(existing?.id) {
        mutableStateOf(existingStdio?.workingDirectory.orEmpty())
    }
    var stdioEnvironment by rememberSaveable(existing?.id) {
        mutableStateOf(existingStdio?.environment?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty())
    }
    var runtimeEnvironment by rememberSaveable(existing?.id) {
        mutableStateOf(existingStdio?.runtimeEnvironment ?: "default")
    }
    val isEditing = existing != null
    val tabs = listOf("HTTP", stringResource(Res.string.settings_stdio))

    Box(Modifier.fillMaxSize().background(AetherBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = sharedSettingsContentTopPadding(), start = 20.dp, end = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(
                    if (isEditing) Res.string.settings_update_mcp_server_description
                    else Res.string.settings_add_mcp_server_page_description
                ),
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
            if (selectedTab == 0) {
                SettingsCardGroup {
                    SharedMcpTextField(stringResource(Res.string.settings_server_name), httpName, { httpName = it })
                    CardDivider()
                    SharedMcpTextField(stringResource(Res.string.settings_server_url), httpUrl, { httpUrl = it })
                    CardDivider()
                    SharedMcpTextField(
                        stringResource(Res.string.settings_headers),
                        httpHeaders,
                        { httpHeaders = it },
                        minLines = 2,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.settings_optional_headers_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                SharedSettingsActionButton(
                    label = stringResource(
                        if (isEditing) Res.string.settings_save_http_server
                        else Res.string.settings_add_http_server
                    ),
                    onClick = {
                        val name = httpName.trim()
                        val url = httpUrl.trim()
                        if (name.isNotBlank() && url.isNotBlank()) onSave(
                            SharedMcpServerConfig(
                                id = existing?.id ?: "mcp-${platformCurrentTimeMillis()}",
                                name = name,
                                actionLabel = generateSharedQuickActionLabel(name, url),
                                transport = SharedMcpTransport.Http,
                                url = url,
                                headers = parseMcpKeyValues(httpHeaders),
                                connectTimeoutMillis = existing?.connectTimeoutMillis ?: 15_000L,
                                requestTimeoutMillis = existing?.requestTimeoutMillis ?: 60_000L,
                                enabled = existing?.enabled ?: true,
                                createdAtMillis = existing?.createdAtMillis ?: platformCurrentTimeMillis(),
                                updatedAtMillis = platformCurrentTimeMillis(),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SettingsCardGroup {
                    SharedMcpTextField(stringResource(Res.string.settings_server_name), stdioName, { stdioName = it })
                    CardDivider()
                    SharedMcpTextField(
                        stringResource(Res.string.settings_command),
                        stdioCommand,
                        { stdioCommand = it },
                        minLines = 2,
                    )
                    CardDivider()
                    SharedMcpTextField(
                        stringResource(Res.string.settings_arguments),
                        stdioArguments,
                        { stdioArguments = it },
                        minLines = 2,
                    )
                    CardDivider()
                    SharedMcpTextField(
                        stringResource(Res.string.settings_working_directory),
                        stdioWorkingDirectory,
                        { stdioWorkingDirectory = it },
                    )
                    CardDivider()
                    SharedMcpTextField(
                        stringResource(Res.string.settings_environment),
                        stdioEnvironment,
                        { stdioEnvironment = it },
                        minLines = 2,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.settings_optional_environment_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
                SettingsCardGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(Res.string.settings_runtime_environment),
                            style = MaterialTheme.typography.labelLarge,
                            color = AetherOnSurface,
                        )
                        Text(
                            stringResource(Res.string.settings_runtime_environment_default_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                        )
                        SharedSettingsChoiceRow(
                            title = stringResource(Res.string.settings_default_runtime),
                            subtitle = stringResource(Res.string.settings_default_runtime_help),
                            selected = runtimeEnvironment == "default",
                            onClick = { runtimeEnvironment = "default" },
                        )
                        SharedSettingsChoiceRow(
                            title = "Alpine",
                            subtitle = stringResource(Res.string.settings_runtime_alpine_stdio_subtitle),
                            selected = runtimeEnvironment == "alpine",
                            onClick = { runtimeEnvironment = "alpine" },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SharedSettingsActionButton(
                    label = stringResource(
                        if (isEditing) Res.string.settings_save_stdio_server
                        else Res.string.settings_add_stdio_server
                    ),
                    onClick = {
                        val name = stdioName.trim()
                        val command = stdioCommand.trim()
                        if (name.isNotBlank() && command.isNotBlank()) onSave(
                            SharedMcpServerConfig(
                                id = existing?.id ?: "mcp-${platformCurrentTimeMillis()}",
                                name = name,
                                actionLabel = generateSharedQuickActionLabel(name, command),
                                transport = SharedMcpTransport.Stdio,
                                command = command,
                                arguments = parseMcpLines(stdioArguments),
                                workingDirectory = stdioWorkingDirectory.trim(),
                                environment = parseMcpKeyValues(stdioEnvironment),
                                runtimeEnvironment = runtimeEnvironment,
                                connectTimeoutMillis = existing?.connectTimeoutMillis ?: 15_000L,
                                requestTimeoutMillis = existing?.requestTimeoutMillis ?: 60_000L,
                                enabled = existing?.enabled ?: true,
                                createdAtMillis = existing?.createdAtMillis ?: platformCurrentTimeMillis(),
                                updatedAtMillis = platformCurrentTimeMillis(),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(32.dp))
        }
        SettingsTopBar(title = stringResource(Res.string.settings_mcp_servers), onBack = onBack)
    }
}

@Composable
private fun SharedMcpTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
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
            minLines = minLines,
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
private fun SharedMcpDetailLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AetherOnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = AetherOnSurface)
    }
}

private fun parseMcpLines(value: String): List<String> =
    value.lineSequence().map(String::trim).filter(String::isNotBlank).toList()

private fun parseMcpKeyValues(value: String): Map<String, String> =
    value.lineSequence().mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) null
        else line.substring(0, separator).trim().takeIf(String::isNotBlank)?.let { key ->
            key to line.substring(separator + 1).trim()
        }
    }.toMap()

private fun formatMcpInspection(
    response: JsonObject,
    operation: SharedMcpInspection,
    title: String,
): String {
    val key = when (operation) {
        SharedMcpInspection.Tools -> "tools"
        SharedMcpInspection.Resources -> "resources"
        SharedMcpInspection.Prompts -> "prompts"
    }
    val items = response[key] as? JsonArray ?: JsonArray(emptyList())
    val nameKey = if (operation == SharedMcpInspection.Resources) "uri" else "name"
    val serverInfo = response["server_info"]?.jsonPrimitive?.contentOrNull
        .orEmpty().ifBlank { response["server_name"]?.jsonPrimitive?.contentOrNull.orEmpty() }
    return buildString {
        append(title)
        append(": ")
        append(items.size)
        if (serverInfo.isNotBlank()) {
            append(" on ")
            append(serverInfo)
        }
        if (items.isNotEmpty()) {
            appendLine()
            items.take(8).forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val name = item[nameKey]?.jsonPrimitive?.contentOrNull
                    ?: item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                append("- ")
                append(name)
                if (description.isNotBlank()) {
                    append(": ")
                    append(description.take(160))
                }
                appendLine()
            }
            if (items.size > 8) {
                append("... and ")
                append(items.size - 8)
                append(" more")
            }
        }
    }.trim()
}
