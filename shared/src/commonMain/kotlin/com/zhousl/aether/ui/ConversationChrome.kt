package com.zhousl.aether.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherSurface

private val ConversationControlShadow = Color(0x14000000)
private val ConversationMotionEasing = CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)

@Composable
fun AetherConversationTopBarFrame(
    menuDescription: String,
    newChatDescription: String,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    showMenu: Boolean = true,
    modifier: Modifier = Modifier,
    centerContent: @Composable BoxScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenu) {
            HeaderCircleButton(
                icon = Icons.Rounded.Menu,
                contentDescription = menuDescription,
                onClick = onMenu,
                size = 38.dp,
                iconSize = 19.dp,
                containerColor = AetherSurface.copy(alpha = 0.96f),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (showMenu) 12.dp else 5.dp,
                    end = 12.dp,
                ),
            content = centerContent,
        )
        HeaderCircleButton(
            icon = LucideIcons.SquarePen,
            contentDescription = newChatDescription,
            onClick = onNewChat,
            size = 38.dp,
            iconSize = 19.dp,
            containerColor = AetherSurface.copy(alpha = 0.96f),
        )
    }
}

@Composable
fun AetherSimpleModelSelector(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .shadow(
                4.dp,
                RoundedCornerShape(999.dp),
                ambientColor = ConversationControlShadow,
                spotColor = ConversationControlShadow,
            )
            .clip(RoundedCornerShape(999.dp))
            .background(AetherSurface.copy(alpha = 0.96f))
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
            color = AetherOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AetherConversationEmptyState(
    welcomeLabel: String,
    analyzeImageLabel: String,
    codeLabel: String,
    helpWriteLabel: String,
    summarizeFileLabel: String,
    inputFocused: Boolean,
    onStarterPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleOffset by animateDpAsState(
        targetValue = if (inputFocused) (-34).dp else (-24).dp,
        animationSpec = tween(durationMillis = 260, easing = ConversationMotionEasing),
        label = "empty_state_title_offset",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .offset(y = titleOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = welcomeLabel,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
            color = AetherOnSurface,
        )
        Spacer(modifier = Modifier.height(26.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConversationStarterChip(
                    icon = Icons.Rounded.Image,
                    label = analyzeImageLabel,
                    iconTint = Color(0xFF38A961),
                    onClick = {
                        onStarterPromptSelected("Analyze this image and describe the important details.")
                    },
                )
                ConversationStarterChip(
                    icon = Icons.Rounded.Terminal,
                    label = codeLabel,
                    iconTint = Color(0xFF7D70DD),
                    onClick = { onStarterPromptSelected("Help me write or debug this code: ") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConversationStarterChip(
                    icon = Icons.Rounded.AutoAwesome,
                    label = helpWriteLabel,
                    iconTint = Color(0xFFE48AAE),
                    onClick = {
                        onStarterPromptSelected("Help me write a clear, polished message about ")
                    },
                )
                ConversationStarterChip(
                    icon = Icons.Rounded.AttachFile,
                    label = summarizeFileLabel,
                    iconTint = Color(0xFF66C7D4),
                    onClick = {
                        onStarterPromptSelected("Summarize this file and list the key points.")
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversationStarterChip(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .shadow(
                6.dp,
                RoundedCornerShape(999.dp),
                ambientColor = ConversationControlShadow,
                spotColor = ConversationControlShadow,
            )
            .clip(RoundedCornerShape(999.dp))
            .background(AetherSurface.copy(alpha = 0.98f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = AetherOnSurfaceVariant,
        )
    }
}
