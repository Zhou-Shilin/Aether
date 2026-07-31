package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.common_chat
import com.zhousl.aether.shared.resources.common_delete
import com.zhousl.aether.shared.resources.common_export
import com.zhousl.aether.shared.resources.common_new_chat
import com.zhousl.aether.shared.resources.common_rename
import com.zhousl.aether.shared.resources.common_search
import com.zhousl.aether.shared.resources.search_no_chats_match
import com.zhousl.aether.shared.resources.settings_title
import com.zhousl.aether.shared.resources.chat_no_conversations_yet
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherScrim
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

data class SharedConversationSummary(
    val id: String,
    val title: String,
    val indicator: SharedConversationIndicator = SharedConversationIndicator.None,
)

enum class SharedConversationIndicator {
    None,
    Working,
    UnviewedComplete,
}

private val SharedDrawerOverlayFadeHeight = 18.dp

@Composable
fun AetherConversationDrawer(
    sessions: List<SharedConversationSummary>,
    selectedSessionId: String,
    onNewChat: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onExportSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSettingsSelected: () -> Unit,
    permanent: Boolean = false,
    extraContent: @Composable ((dismissSearch: () -> Unit) -> Unit) = {},
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
        if (query.isBlank()) sessions else sessions.filter { it.title.lowercase().contains(query) }
    }
    val dismissSearch = {
        searchExpanded = false
        searchQuery = ""
    }
    val drawerBackground = if (permanent) AetherBackground else AetherSurface

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().width(if (permanent) 320.dp else 322.dp),
        drawerContainerColor = drawerBackground,
        drawerShape = if (permanent) RoundedCornerShape(0.dp) else {
            RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
        },
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 18.dp)) {
            if (filteredSessions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = overlayHeight - SharedDrawerOverlayFadeHeight,
                            bottom = 96.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (sessions.isEmpty()) {
                                Res.string.chat_no_conversations_yet
                            } else {
                                Res.string.search_no_chats_match
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                    extraContent(dismissSearch)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = overlayHeight - SharedDrawerOverlayFadeHeight,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        SharedDrawerSessionRow(
                            session = session,
                            selected = session.id == selectedSessionId,
                            onClick = {
                                dismissSearch()
                                onSessionSelected(session.id)
                            },
                            onRename = { onRenameSession(session.id, it) },
                            onExport = { onExportSession(session.id) },
                            onDelete = { onDeleteSession(session.id) },
                        )
                    }
                    item(key = "aether-extra-drawer-content") {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            extraContent(dismissSearch)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(sharedDrawerOverlayBodyGradient(drawerBackground))
                    .onSizeChanged { overlayHeightPx = it.height },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp)
                        .statusBarsPadding(),
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
                                contentDescription = stringResource(Res.string.common_search),
                                onClick = {
                                    if (searchExpanded || searchQuery.isNotBlank()) dismissSearch()
                                    else searchExpanded = true
                                },
                                size = 46.dp,
                                containerColor = AetherSurface.copy(alpha = 0.90f),
                            )
                            HeaderCircleButton(
                                icon = LucideIcons.Settings,
                                contentDescription = stringResource(Res.string.settings_title),
                                onClick = {
                                    dismissSearch()
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
                            SharedDrawerSearchField(searchQuery) { searchQuery = it }
                        }
                    }
                    Spacer(modifier = Modifier.height(if (searchExpanded || searchQuery.isNotBlank()) 10.dp else 12.dp))
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SharedDrawerOverlayFadeHeight)
                        .background(sharedDrawerOverlayTailGradient(drawerBackground)),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 18.dp)
                    .shadow(18.dp, RoundedCornerShape(999.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF7A4DFF), Color(0xFF925BFF))))
                    .clickable {
                        dismissSearch()
                        onNewChat()
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(LucideIcons.SquarePen, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Text(
                    text = stringResource(Res.string.common_chat),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SharedDrawerSearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AetherScrim, spotColor = AetherScrim)
            .clip(RoundedCornerShape(24.dp))
            .background(AetherSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(LucideIcons.Search, null, tint = AetherOnSurfaceVariant, modifier = Modifier.size(18.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) {
                Text(
                    stringResource(Res.string.common_search),
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
private fun SharedDrawerSessionRow(
    session: SharedConversationSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val newChatTitle = stringResource(Res.string.common_new_chat)
    var menuExpanded by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var renameFieldHadFocus by remember { mutableStateOf(false) }
    var renameFocusRequest by remember { mutableIntStateOf(0) }
    var titleValue by remember(session.id, session.title, newChatTitle) {
        mutableStateOf(session.title.ifBlank { newChatTitle })
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun commitRename() {
        titleValue.trim().takeIf { it.isNotBlank() && it != session.title }?.let(onRename)
        isRenaming = false
        keyboard?.hide()
    }

    LaunchedEffect(renameFocusRequest, isRenaming) {
        if (renameFocusRequest > 0 && isRenaming) {
            delay(260)
            if (isRenaming) {
                focusRequester.requestFocus()
                keyboard?.show()
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
                    if (isRenaming) Modifier else Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuExpanded = true },
                    )
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
                        }
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
            if (session.indicator != SharedConversationIndicator.None) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (session.indicator == SharedConversationIndicator.Working) {
                                Color(0xFF22C55E)
                            } else {
                                Color(0xFF3B82F6)
                            }
                        )
                )
            }
        }
        SharedDrawerActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
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
private fun SharedDrawerActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, 34),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
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
                SharedDrawerActionRow(stringResource(Res.string.common_rename), onRename)
                SharedDrawerActionRow(stringResource(Res.string.common_export), onExport)
                SharedDrawerActionRow(stringResource(Res.string.common_delete), onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun SharedDrawerActionRow(
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

private fun sharedDrawerOverlayBodyGradient(baseColor: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to baseColor.copy(alpha = 0.94f),
        0.20f to baseColor.copy(alpha = 0.86f),
        0.48f to baseColor.copy(alpha = 0.54f),
        0.78f to baseColor.copy(alpha = 0.18f),
        1.0f to Color.Transparent,
    )
)

private fun sharedDrawerOverlayTailGradient(baseColor: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to baseColor.copy(alpha = 0.18f),
        0.46f to baseColor.copy(alpha = 0.06f),
        1.0f to Color.Transparent,
    )
)
