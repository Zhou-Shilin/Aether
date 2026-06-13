package com.zhousl.aether.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zhousl.aether.R
import com.zhousl.aether.data.InstalledSkill
import com.zhousl.aether.data.AppLanguage
import com.zhousl.aether.data.AgentModeDisplayState
import com.zhousl.aether.data.McpServerConfig
import com.zhousl.aether.data.McpTransportConfig
import com.zhousl.aether.data.PendingSessionInput
import com.zhousl.aether.data.ProviderModelOption
import com.zhousl.aether.data.SessionExecutionState
import com.zhousl.aether.data.SessionFollowUpMode
import com.zhousl.aether.data.quickActionLabel
import com.zhousl.aether.termux.TermuxSetupState
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherBackgroundGradientTop
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import org.json.JSONObject

private val DrawerOverlayFadeHeight = 18.dp

private fun drawerOverlayBodyGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherSurface.copy(alpha = 0.94f),
        0.20f to AetherSurface.copy(alpha = 0.86f),
        0.48f to AetherSurface.copy(alpha = 0.54f),
        0.78f to AetherSurface.copy(alpha = 0.18f),
        1.0f to Color.Transparent,
    )
)

private fun drawerOverlayTailGradient(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to AetherSurface.copy(alpha = 0.18f),
        0.46f to AetherSurface.copy(alpha = 0.06f),
        1.0f to Color.Transparent,
    )
)
@Composable
fun ConversationDrawer(
    sessions: List<ChatSession>,
    selectedSessionId: String,
    sessionExecutionStates: Map<String, SessionExecutionState>,
    unviewedCompletedSessionIds: Set<String>,
    onNewChat: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (ChatSession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSettingsSelected: () -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val overlayHeight = with(density) {
        if (overlayHeightPx > 0) overlayHeightPx.toDp() else 132.dp
    }
    val filteredSessions = remember(sessions, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            sessions
        } else {
            sessions.filter { session -> session.title.lowercase().contains(query) }
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 304.dp, max = 328.dp),
        drawerContainerColor = AetherSurface,
        drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 18.dp)
        ) {
            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = overlayHeight - DrawerOverlayFadeHeight,
                            bottom = 96.dp,
                        )
                ) {
                    Text(
                        text = if (sessions.isEmpty()) {
                            stringResource(R.string.chat_no_conversations_yet)
                        } else {
                            stringResource(R.string.search_no_chats_match)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = overlayHeight - DrawerOverlayFadeHeight,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        DrawerSessionRow(
                            session = session,
                            selected = session.id == selectedSessionId,
                            indicator = when {
                                sessionExecutionStates[session.id]?.isRunning == true -> DrawerSessionIndicator.Working
                                unviewedCompletedSessionIds.contains(session.id) -> DrawerSessionIndicator.UnviewedComplete
                                else -> DrawerSessionIndicator.None
                            },
                            onClick = {
                                searchExpanded = false
                                searchQuery = ""
                                onSessionSelected(session.id)
                            },
                            onRename = { title -> onRenameSession(session.id, title) },
                            onExport = { onExportSession(session) },
                            onDelete = { onDeleteSession(session.id) },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(drawerOverlayBodyGradient())
                    .onSizeChanged { overlayHeightPx = it.height }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp)
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Aether",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AetherOnSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeaderCircleButton(
                                icon = LucideIcons.Search,
                                contentDescription = stringResource(R.string.common_search),
                                onClick = {
                                    if (searchExpanded || searchQuery.isNotBlank()) {
                                        searchExpanded = false
                                        searchQuery = ""
                                    } else {
                                        searchExpanded = true
                                    }
                                },
                                size = 46.dp,
                                containerColor = AetherSurface.copy(alpha = 0.90f),
                            )
                            HeaderCircleButton(
                                icon = LucideIcons.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                onClick = {
                                    searchExpanded = false
                                    searchQuery = ""
                                    onSettingsSelected()
                                },
                                size = 46.dp,
                                containerColor = AetherSurface.copy(alpha = 0.90f),
                            )
                        }
                    }

                    AnimatedVisibility(visible = searchExpanded || searchQuery.isNotBlank()) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            DrawerCompactSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (searchExpanded || searchQuery.isNotBlank()) 10.dp else 12.dp))
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DrawerOverlayFadeHeight)
                        .background(drawerOverlayTailGradient())
                )
            }

            DrawerFloatingChatButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 18.dp),
                onClick = {
                    searchExpanded = false
                    searchQuery = ""
                    onNewChat()
                },
            )
        }
    }
}

private enum class DrawerSessionIndicator {
    None,
    Working,
    UnviewedComplete,
}

@Composable
private fun DrawerCompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = LucideIcons.Search,
            contentDescription = null,
            tint = AetherOnSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = stringResource(R.string.common_search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AetherOnSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AetherOnSurface),
                cursorBrush = SolidColor(AetherOnSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSessionRow(
    session: ChatSession,
    selected: Boolean,
    indicator: DrawerSessionIndicator,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val newChatTitle = stringResource(R.string.common_new_chat)
    var menuExpanded by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var renameFieldHadFocus by remember { mutableStateOf(false) }
    var renameFocusRequest by remember { mutableIntStateOf(0) }
    var titleValue by remember(session.id, session.title, newChatTitle) { mutableStateOf(session.title.ifBlank { newChatTitle }) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun commitRename() {
        val trimmed = titleValue.trim()
        if (trimmed.isNotBlank() && trimmed != session.title) {
            onRename(trimmed)
        }
        isRenaming = false
        keyboardController?.hide()
    }

    LaunchedEffect(renameFocusRequest, isRenaming) {
        if (renameFocusRequest > 0 && isRenaming) {
            delay(260)
            if (isRenaming) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) AetherSurfaceHigh else Color.Transparent)
                .then(
                    if (isRenaming) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(
                            onClick = {
                                onClick()
                            },
                            onLongClick = { menuExpanded = true },
                        )
                    }
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRenaming) {
                BasicTextField(
                    value = titleValue,
                    onValueChange = { titleValue = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = AetherOnSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(AetherOnSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitRename() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                renameFieldHadFocus = true
                            } else if (renameFieldHadFocus && isRenaming) {
                                commitRename()
                            }
                        },
                )
            } else {
                Text(
                    text = session.title.ifBlank { newChatTitle },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = AetherOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (indicator != DrawerSessionIndicator.None) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (indicator) {
                                DrawerSessionIndicator.Working -> Color(0xFF22C55E)
                                DrawerSessionIndicator.UnviewedComplete -> Color(0xFF3B82F6)
                                DrawerSessionIndicator.None -> Color.Transparent
                            }
                        )
                )
            }
        }

        DrawerSessionActionMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            onRename = {
                menuExpanded = false
                titleValue = session.title.ifBlank { newChatTitle }
                renameFieldHadFocus = false
                isRenaming = true
                renameFocusRequest += 1
            },
            onExport = {
                menuExpanded = false
                onExport()
            },
            onDelete = {
                menuExpanded = false
                onDelete()
            },
        )
    }
}

@Composable
private fun DrawerSessionActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val menuVisibility = remember { MutableTransitionState(false) }
    menuVisibility.targetState = expanded
    if (!menuVisibility.currentState && !menuVisibility.targetState) return

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 34),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = menuVisibility,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 188.dp, max = 220.dp)
                    .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AetherSurface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DrawerSessionActionRow(stringResource(R.string.common_rename), onRename)
                DrawerSessionActionRow(stringResource(R.string.common_export), onExport)
                DrawerSessionActionRow(stringResource(R.string.common_delete), onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun DrawerSessionActionRow(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) Color(0xFFB42318) else AetherOnSurface,
        )
    }
}

private fun pendingInputSummary(
    pendingInputs: List<PendingSessionInput>,
): String? {
    if (pendingInputs.isEmpty()) return null
    val queuedCount = pendingInputs.count { it.mode == SessionFollowUpMode.Queue }
    val steerCount = pendingInputs.count { it.mode == SessionFollowUpMode.Steer }
    return buildList {
        if (queuedCount > 0) {
            add("$queuedCount queued")
        }
        if (steerCount > 0) {
            add("$steerCount steer")
        }
    }.joinToString(" · ")
}

@Composable
private fun DrawerFloatingChatButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(999.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF7A4DFF), Color(0xFF925BFF)),
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = LucideIcons.SquarePen,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = stringResource(R.string.common_chat),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
        )
    }
}

@Composable
internal fun HeaderCircleButton(
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    containerColor: Color = Color.White,
    iconTint: Color = AetherOnSurface,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(12.dp, CircleShape, ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(CircleShape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.55f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            iconPainter != null -> Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            )
        }
    }
}