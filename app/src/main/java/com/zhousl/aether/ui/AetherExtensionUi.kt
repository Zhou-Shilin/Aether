package com.zhousl.aether.ui

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zhousl.aether.data.AetherAppExtensionPage
import com.zhousl.aether.data.AetherAppExtensionSnapshot
import com.zhousl.aether.mod.AetherNativeComponentContext
import com.zhousl.aether.mod.AetherNativeComponentMode
import com.zhousl.aether.mod.AetherNativeComponentRegistration
import com.zhousl.aether.mod.AetherNativeHost
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import org.json.JSONArray
import org.json.JSONObject

const val AetherExtensionSlotAppOverlay = "app.overlay"
const val AetherExtensionSlotChatTop = "chat.top"
const val AetherExtensionSlotChatEmpty = "chat.empty"
const val AetherExtensionSlotChatListStart = "chat.list.start"
const val AetherExtensionSlotChatListEnd = "chat.list.end"
const val AetherExtensionSlotChatComposerTop = "chat.composer.top"
const val AetherExtensionSlotSettingsHub = "settings.hub"
const val AetherExtensionSlotDrawer = "drawer"
const val AetherExtensionSlotDrawerHeader = "drawer.header"
const val AetherExtensionSlotDrawerFooter = "drawer.footer"
const val AetherExtensionSlotDrawerListEnd = "drawer.list.end"

const val AetherExtensionComponentChatComposerActionTray = "chat.composer.actionTray"
const val AetherExtensionComponentChatComposerSkillPicker = "chat.composer.skillPicker"
const val AetherExtensionComponentAppContent = "app.content"
const val AetherExtensionComponentChatScreen = "chat.screen"
const val AetherExtensionComponentSettingsScreen = "settings.screen"

@Immutable
data class AetherExtensionUiController(
    val snapshot: AetherAppExtensionSnapshot,
    val runtimeError: String,
    val nativeComponents: List<AetherNativeComponentRegistration>,
    val uiState: AetherUiState,
    val publicState: JSONObject,
    val onHostCall: suspend (String, JSONObject) -> JSONObject,
    val onAction: (String, String, JSONObject) -> Unit,
    val onOpenPage: (String) -> Unit,
)

val LocalAetherExtensionUiController =
    staticCompositionLocalOf<AetherExtensionUiController?> { null }

@Composable
fun AetherExtensionSlot(
    slot: String,
    modifier: Modifier = Modifier,
    spacing: Int = 10,
) {
    val controller = LocalAetherExtensionUiController.current ?: return
    val surfaces = controller.snapshot.surfacesAt(slot)
    if (surfaces.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) {
        surfaces.forEach { surface ->
            key(surface.id) {
                AetherExtensionView(
                    value = surface.tree,
                    extensionId = surface.extensionId,
                )
            }
        }
    }
}

@Composable
fun AetherExtensionOverlaySlot(
    modifier: Modifier = Modifier,
) {
    val controller = LocalAetherExtensionUiController.current ?: return
    val surfaces = controller.snapshot.surfacesAt(AetherExtensionSlotAppOverlay)
    if (surfaces.isEmpty()) return
    Box(modifier = modifier) {
        surfaces.forEach { surface ->
            key(surface.id) {
                AetherExtensionView(
                    value = surface.tree,
                    extensionId = surface.extensionId,
                )
            }
        }
    }
}

@Composable
fun AetherExtensionComponentHost(
    target: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val controller = LocalAetherExtensionUiController.current
    val nativeComponents = controller?.nativeComponents
        ?.filter { it.target == target }
        ?.sortedWith(
            compareBy<AetherNativeComponentRegistration> { it.priority }
                .thenBy { it.sequence }
        )
        .orEmpty()
    if (nativeComponents.isEmpty()) {
        AetherScriptExtensionComponentHost(
            target = target,
            modifier = modifier,
            content = content,
        )
        return
    }

    val nativeController = requireNotNull(controller)
    val nativeContext = AetherNativeComponentContext(
        target = target,
        uiState = nativeController.uiState,
        publicState = nativeController.publicState,
        host = AetherNativeHost { method, args ->
            nativeController.onHostCall(method, args)
        },
    )
    val before = nativeComponents.filter { it.mode == AetherNativeComponentMode.Before }
    val after = nativeComponents.filter { it.mode == AetherNativeComponentMode.After }
    val decisive = nativeComponents.lastOrNull { component ->
        component.mode == AetherNativeComponentMode.Replace ||
            component.mode == AetherNativeComponentMode.Hide
    }
    var center: @Composable () -> Unit = when (decisive?.mode) {
        AetherNativeComponentMode.Hide -> ({})
        AetherNativeComponentMode.Replace -> ({
            decisive.renderer?.render(nativeContext) {}
        })
        else -> ({
            AetherScriptExtensionComponentHost(
                target = target,
                content = content,
            )
        })
    }
    nativeComponents
        .filter { it.mode == AetherNativeComponentMode.Wrap }
        .forEach { component ->
            val nested = center
            center = {
                component.renderer?.render(nativeContext, nested)
            }
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        before.forEach { component ->
            key("${component.owner}:${component.id}") {
                component.renderer?.render(nativeContext) {}
            }
        }
        center()
        after.forEach { component ->
            key("${component.owner}:${component.id}") {
                component.renderer?.render(nativeContext) {}
            }
        }
    }
}

@Composable
private fun AetherScriptExtensionComponentHost(
    target: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val controller = LocalAetherExtensionUiController.current
    val components = controller?.snapshot?.componentsAt(target).orEmpty()
    if (components.isEmpty()) {
        content()
        return
    }

    val before = components.filter { it.mode.equals("before", ignoreCase = true) }
    val after = components.filter { it.mode.equals("after", ignoreCase = true) }
    val decisive = components.lastOrNull { component ->
        component.mode.equals("replace", ignoreCase = true) ||
            component.mode.equals("hide", ignoreCase = true)
    }
    var center: @Composable () -> Unit = when {
        decisive?.mode.equals("hide", ignoreCase = true) -> ({})
        decisive?.mode.equals("replace", ignoreCase = true) -> ({
            AetherExtensionView(
                value = decisive?.tree,
                extensionId = decisive?.extensionId.orEmpty(),
            )
        })
        else -> content
    }
    components
        .filter { it.mode.equals("wrap", ignoreCase = true) }
        .forEach { component ->
            val nested = center
            center = {
                AetherExtensionView(
                    value = component.tree,
                    extensionId = component.extensionId,
                    nativeContent = nested,
                )
            }
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        before.forEach { component ->
            key(component.id) {
                AetherExtensionView(
                    value = component.tree,
                    extensionId = component.extensionId,
                )
            }
        }
        center()
        after.forEach { component ->
            key(component.id) {
                AetherExtensionView(
                    value = component.tree,
                    extensionId = component.extensionId,
                )
            }
        }
    }
}

@Composable
fun AetherExtensionPageScreen(
    page: AetherAppExtensionPage,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AetherBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AetherBackground)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = AetherOnSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = AetherOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (page.subtitle.isNotBlank()) {
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = AetherOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            AetherExtensionView(
                value = page.tree,
                extensionId = page.extensionId,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AetherExtensionPageLauncher(
    page: AetherAppExtensionPage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AetherSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AetherSurfaceHigher),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = extensionIcon(page.icon),
                contentDescription = null,
                tint = AetherOnSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.bodyLarge,
                color = AetherOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (page.subtitle.isNotBlank()) {
                Text(
                    text = page.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AetherOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AetherExtensionView(
    value: Any?,
    extensionId: String,
    modifier: Modifier = Modifier,
    nativeContent: (@Composable () -> Unit)? = null,
) {
    when (value) {
        null,
        JSONObject.NULL -> Unit

        is String -> Text(
            text = value,
            color = AetherOnSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )

        is JSONArray -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (index in 0 until value.length()) {
                key(index) {
                    AetherExtensionView(
                        value = value.opt(index),
                        extensionId = extensionId,
                        nativeContent = nativeContent,
                    )
                }
            }
        }

        is JSONObject -> AetherExtensionNode(
            node = value,
            extensionId = extensionId,
            modifier = modifier,
            nativeContent = nativeContent,
        )

        else -> Text(
            text = value.toString(),
            color = AetherOnSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AetherExtensionNode(
    node: JSONObject,
    extensionId: String,
    modifier: Modifier = Modifier,
    nativeContent: (@Composable () -> Unit)? = null,
) {
    val controller = LocalAetherExtensionUiController.current ?: return
    val type = node.optString("type", "column")
    val resolvedModifier = nodeModifier(modifier, node)
    val action = node.optString("action").trim()
    val actionArgs = node.optJSONObject("args") ?: JSONObject()
    val clickableModifier = if (action.isNotBlank()) {
        resolvedModifier.clickable {
            controller.onAction(extensionId, action, actionArgs)
        }
    } else {
        resolvedModifier
    }

    when (type.lowercase()) {
        "text",
        "code" -> {
            val text = node.optString("text")
            Text(
                text = text,
                modifier = clickableModifier,
                color = extensionColor(node.optString("color")).takeUnless {
                    node.optString("color").isBlank()
                } ?: AetherOnSurface,
                style = extensionTextStyle(node, code = type.equals("code", ignoreCase = true)),
                fontWeight = extensionFontWeight(node.optString("weight")),
                textAlign = extensionTextAlign(node.optString("align")),
                maxLines = node.optInt("maxLines", Int.MAX_VALUE).coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
            )
        }

        "row" -> {
            val rowModifier = if (node.has("width")) {
                clickableModifier
            } else {
                clickableModifier.fillMaxWidth()
            }
            if (node.optBoolean("wrap")) {
                FlowRow(
                    modifier = rowModifier,
                    horizontalArrangement = extensionHorizontalArrangement(node.optString("arrangement")),
                    verticalArrangement = Arrangement.spacedBy(
                        node.optDouble("rowSpacing", 8.0).dp
                    ),
                    maxItemsInEachRow = node.optInt(
                        "maxItemsInEachRow",
                        Int.MAX_VALUE,
                    ).coerceAtLeast(1),
                ) {
                    renderChildren(node.optJSONArray("children"), extensionId, nativeContent)
                }
            } else {
                Row(
                    modifier = rowModifier,
                    horizontalArrangement = extensionHorizontalArrangement(node.optString("arrangement")),
                    verticalAlignment = extensionVerticalAlignment(node.optString("verticalAlignment")),
                ) {
                    renderRowChildren(node.optJSONArray("children"), extensionId, nativeContent)
                }
            }
        }

        "box" -> Box(
            modifier = clickableModifier,
            contentAlignment = extensionBoxAlignment(node.optString("alignment")),
        ) {
            renderChildren(node.optJSONArray("children"), extensionId, nativeContent)
        }

        "card" -> Column(
            modifier = clickableModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(node.optDouble("radius", 20.0).dp))
                .background(cardColor(node.optString("tone")))
                .padding(node.optDouble("contentPadding", 16.0).dp),
            verticalArrangement = Arrangement.spacedBy(node.optDouble("spacing", 8.0).dp),
        ) {
            renderChildren(node.optJSONArray("children"), extensionId, nativeContent)
        }

        "scroll",
        "column" -> {
            val scrollable = node.optBoolean("scroll", type.equals("scroll", ignoreCase = true))
            Column(
                modifier = if (scrollable) {
                    clickableModifier.verticalScroll(rememberScrollState())
                } else {
                    clickableModifier
                },
                verticalArrangement = extensionVerticalArrangement(node.optString("arrangement")),
                horizontalAlignment = extensionHorizontalAlignment(node.optString("horizontalAlignment")),
            ) {
                renderChildren(node.optJSONArray("children"), extensionId, nativeContent)
            }
        }

        "core",
        "next" -> Box(modifier = resolvedModifier) {
            nativeContent?.invoke()
        }

        "button" -> Button(
            onClick = {
                if (action.isNotBlank()) {
                    controller.onAction(extensionId, action, actionArgs)
                }
            },
            enabled = node.optBoolean("enabled", true),
            modifier = resolvedModifier,
            shape = RoundedCornerShape(node.optDouble("radius", 18.0).dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (node.optString("tone")) {
                    "neutral", "secondary" -> AetherSurfaceHigher
                    "danger", "error" -> MaterialTheme.colorScheme.errorContainer
                    else -> AetherPrimary
                },
                contentColor = when (node.optString("tone")) {
                    "neutral", "secondary" -> AetherOnSurface
                    "danger", "error" -> MaterialTheme.colorScheme.onErrorContainer
                    else -> AetherOnPrimary
                },
            ),
        ) {
            node.optString("icon").takeIf(String::isNotBlank)?.let { icon ->
                Icon(
                    imageVector = extensionIcon(icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = node.optString("label", node.optString("text")),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        "iconbutton" -> IconButton(
            onClick = {
                if (action.isNotBlank()) {
                    controller.onAction(extensionId, action, actionArgs)
                }
            },
            enabled = node.optBoolean("enabled", true),
            modifier = resolvedModifier
                .clip(CircleShape)
                .background(AetherSurfaceHigh),
        ) {
            Icon(
                imageVector = extensionIcon(node.optString("icon")),
                contentDescription = node.optString("contentDescription").ifBlank { null },
                tint = extensionColor(node.optString("color")).takeUnless {
                    node.optString("color").isBlank()
                } ?: AetherOnSurface,
            )
        }

        "switch" -> {
            val checked = node.optBoolean("checked")
            Row(
                modifier = resolvedModifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AetherSurfaceHigh)
                    .clickable {
                        if (action.isNotBlank()) {
                            controller.onAction(
                                extensionId,
                                action,
                                JSONObject(actionArgs.toString()).put("checked", !checked),
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.optString("label"),
                        color = AetherOnSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    node.optString("subtitle").takeIf(String::isNotBlank)?.let { subtitle ->
                        Text(
                            text = subtitle,
                            color = AetherOnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = { next ->
                        if (action.isNotBlank()) {
                            controller.onAction(
                                extensionId,
                                action,
                                JSONObject(actionArgs.toString()).put("checked", next),
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AetherPrimary,
                        checkedThumbColor = AetherOnPrimary,
                    ),
                )
            }
        }

        "input" -> {
            val externalValue = node.optString("value")
            var value by remember(node.optString("id"), externalValue) {
                mutableStateOf(externalValue)
            }
            val submit: () -> Unit = {
                if (action.isNotBlank()) {
                    controller.onAction(
                        extensionId,
                        action,
                        JSONObject(actionArgs.toString()).put("value", value),
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    value = next
                    if (node.optString("dispatch") == "change") submit()
                },
                modifier = resolvedModifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(node.optDouble("radius", 18.0).dp))
                    .background(AetherSurfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                enabled = node.optBoolean("enabled", true),
                singleLine = node.optBoolean("singleLine", true),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AetherOnSurface),
                cursorBrush = SolidColor(AetherPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                decorationBox = { innerField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = node.optString("placeholder"),
                                color = AetherOnSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        innerField()
                    }
                },
            )
        }

        "spacer" -> {
            val size = node.optDouble("size", 8.0).dp
            Spacer(
                modifier = resolvedModifier
                    .height(node.optDouble("height", size.value.toDouble()).dp)
                    .width(node.optDouble("width", size.value.toDouble()).dp)
            )
        }

        "progress" -> {
            val value = node.optDouble("value", Double.NaN)
            if (value.isNaN()) {
                CircularProgressIndicator(
                    modifier = resolvedModifier.size(node.optDouble("size", 24.0).dp),
                    strokeWidth = node.optDouble("strokeWidth", 2.0).dp,
                    color = AetherPrimary,
                )
            } else {
                CircularProgressIndicator(
                    progress = { value.toFloat().coerceIn(0f, 1f) },
                    modifier = resolvedModifier.size(node.optDouble("size", 24.0).dp),
                    strokeWidth = node.optDouble("strokeWidth", 2.0).dp,
                    color = AetherPrimary,
                )
            }
        }

        "web" -> AetherExtensionWebView(
            node = node,
            extensionId = extensionId,
            modifier = resolvedModifier,
        )

        "pagebutton" -> {
            val pageId = node.optString("page")
            Button(
                onClick = {
                    controller.onOpenPage(
                        if (pageId.contains(':')) pageId else "$extensionId:$pageId"
                    )
                },
                modifier = resolvedModifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AetherSurfaceHigher,
                    contentColor = AetherOnSurface,
                ),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = extensionIcon(node.optString("icon")),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = node.optString("label"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        else -> Column(
            modifier = clickableModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            renderChildren(node.optJSONArray("children"), extensionId, nativeContent)
        }
    }
}

@Composable
private fun renderChildren(
    children: JSONArray?,
    extensionId: String,
    nativeContent: (@Composable () -> Unit)? = null,
) {
    if (children == null) return
    for (index in 0 until children.length()) {
        key(index) {
            AetherExtensionView(
                value = children.opt(index),
                extensionId = extensionId,
                nativeContent = nativeContent,
            )
        }
    }
}

@Composable
private fun RowScope.renderRowChildren(
    children: JSONArray?,
    extensionId: String,
    nativeContent: (@Composable () -> Unit)? = null,
) {
    if (children == null) return
    for (index in 0 until children.length()) {
        key(index) {
            val child = children.opt(index)
            val weight = (child as? JSONObject)
                ?.optDoubleOrNull("weight")
                ?.toFloat()
                ?.takeIf { it > 0f }
            AetherExtensionView(
                value = child,
                extensionId = extensionId,
                modifier = if (weight != null) Modifier.weight(weight) else Modifier,
                nativeContent = nativeContent,
            )
        }
    }
}

@Composable
private fun nodeModifier(
    base: Modifier,
    node: JSONObject,
): Modifier {
    var modifier = base
    when (node.optString("width")) {
        "fill", "match" -> modifier = modifier.fillMaxWidth()
        "full" -> modifier = modifier.fillMaxSize()
        else -> node.optDoubleOrNull("width")?.let { modifier = modifier.width(it.dp) }
    }
    when (node.optString("height")) {
        "fill", "match" -> modifier = modifier.fillMaxHeight()
        "full" -> modifier = modifier.fillMaxSize()
        else -> node.optDoubleOrNull("height")?.let { modifier = modifier.height(it.dp) }
    }
    node.optDoubleOrNull("minHeight")?.let { modifier = modifier.heightIn(min = it.dp) }
    node.optDoubleOrNull("maxHeight")?.let { modifier = modifier.heightIn(max = it.dp) }
    node.optDoubleOrNull("alpha")?.let { modifier = modifier.alpha(it.toFloat().coerceIn(0f, 1f)) }
    val radius = node.optDouble("radius", 0.0)
    val background = node.optString("background")
    if (background.isNotBlank()) {
        val shape = RoundedCornerShape(radius.dp)
        modifier = modifier
            .clip(shape)
            .background(extensionColor(background))
    }
    val padding = node.opt("padding")
    modifier = when (padding) {
        is Number -> modifier.padding(padding.toDouble().dp)
        is JSONObject -> modifier.padding(
            start = padding.optDouble("start", padding.optDouble("horizontal", 0.0)).dp,
            top = padding.optDouble("top", padding.optDouble("vertical", 0.0)).dp,
            end = padding.optDouble("end", padding.optDouble("horizontal", 0.0)).dp,
            bottom = padding.optDouble("bottom", padding.optDouble("vertical", 0.0)).dp,
        )
        else -> modifier
    }
    if (node.optBoolean("horizontalScroll")) {
        modifier = modifier.horizontalScroll(rememberScrollState())
    }
    return modifier
}

@Composable
private fun extensionTextStyle(
    node: JSONObject,
    code: Boolean,
) = when (node.optString("style").lowercase()) {
    "display" -> MaterialTheme.typography.displaySmall
    "headline" -> MaterialTheme.typography.headlineSmall
    "title" -> MaterialTheme.typography.titleLarge
    "subtitle" -> MaterialTheme.typography.titleMedium
    "label" -> MaterialTheme.typography.labelLarge
    "caption", "small" -> MaterialTheme.typography.bodySmall
    else -> MaterialTheme.typography.bodyMedium
}.let { style ->
    val withSize = node.optDoubleOrNull("fontSize")?.let {
        style.copy(fontSize = it.sp)
    } ?: style
    if (code || node.optBoolean("monospace")) {
        withSize.copy(fontFamily = FontFamily.Monospace)
    } else {
        withSize
    }
}

private fun extensionFontWeight(value: String): FontWeight? = when (value.lowercase()) {
    "thin" -> FontWeight.Thin
    "light" -> FontWeight.Light
    "medium" -> FontWeight.Medium
    "semibold", "semi-bold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    "black" -> FontWeight.Black
    else -> null
}

private fun extensionTextAlign(value: String): TextAlign = when (value.lowercase()) {
    "center" -> TextAlign.Center
    "end", "right" -> TextAlign.End
    "justify" -> TextAlign.Justify
    else -> TextAlign.Start
}

private fun extensionHorizontalArrangement(value: String): Arrangement.Horizontal = when (value.lowercase()) {
    "center" -> Arrangement.Center
    "end" -> Arrangement.End
    "spacebetween", "space-between" -> Arrangement.SpaceBetween
    "spacearound", "space-around" -> Arrangement.SpaceAround
    "spaceevenly", "space-evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.spacedBy(8.dp)
}

private fun extensionVerticalArrangement(value: String): Arrangement.Vertical = when (value.lowercase()) {
    "center" -> Arrangement.Center
    "bottom", "end" -> Arrangement.Bottom
    "spacebetween", "space-between" -> Arrangement.SpaceBetween
    "spacearound", "space-around" -> Arrangement.SpaceAround
    "spaceevenly", "space-evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.spacedBy(8.dp)
}

private fun extensionVerticalAlignment(value: String): Alignment.Vertical = when (value.lowercase()) {
    "top", "start" -> Alignment.Top
    "bottom", "end" -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

private fun extensionHorizontalAlignment(value: String): Alignment.Horizontal = when (value.lowercase()) {
    "center" -> Alignment.CenterHorizontally
    "end", "right" -> Alignment.End
    else -> Alignment.Start
}

private fun extensionBoxAlignment(value: String): Alignment = when (value.lowercase()) {
    "topcenter" -> Alignment.TopCenter
    "topend", "topright" -> Alignment.TopEnd
    "centerstart", "centerleft" -> Alignment.CenterStart
    "centerend", "centerright" -> Alignment.CenterEnd
    "bottomstart", "bottomleft" -> Alignment.BottomStart
    "bottomcenter" -> Alignment.BottomCenter
    "bottomend", "bottomright" -> Alignment.BottomEnd
    "center" -> Alignment.Center
    else -> Alignment.TopStart
}

@Composable
private fun cardColor(tone: String): Color = when (tone.lowercase()) {
    "primary", "accent" -> AetherPrimary.copy(alpha = 0.16f)
    "error", "danger" -> MaterialTheme.colorScheme.errorContainer
    "higher" -> AetherSurfaceHigher
    else -> AetherSurfaceHigh
}

@Composable
private fun extensionColor(value: String): Color = when (value.lowercase()) {
    "background" -> AetherBackground
    "surface" -> AetherSurface
    "surfacehigh", "surface-high" -> AetherSurfaceHigh
    "surfacehigher", "surface-higher" -> AetherSurfaceHigher
    "primary", "accent" -> AetherPrimary
    "onprimary", "on-primary" -> AetherOnPrimary
    "onsurface", "on-surface", "text" -> AetherOnSurface
    "muted", "secondary", "onsurfacevariant", "on-surface-variant" -> AetherOnSurfaceVariant
    "error", "danger" -> MaterialTheme.colorScheme.error
    "transparent" -> Color.Transparent
    else -> parseHexColor(value) ?: AetherOnSurface
}

private fun parseHexColor(value: String): Color? {
    if (!value.startsWith("#")) return null
    return runCatching {
        Color(android.graphics.Color.parseColor(value))
    }.getOrNull()
}

private fun extensionIcon(name: String): ImageVector = when (name.lowercase()) {
    "add", "plus" -> Icons.Rounded.Add
    "auto", "sparkles", "magic" -> Icons.Rounded.AutoAwesome
    "code" -> Icons.Rounded.Code
    "home" -> Icons.Rounded.Home
    "info" -> Icons.Rounded.Info
    "play", "run" -> Icons.Rounded.PlayArrow
    "refresh", "reload" -> Icons.Rounded.Refresh
    "settings" -> Icons.Rounded.Settings
    "terminal" -> Icons.Rounded.Terminal
    "warning" -> Icons.Rounded.WarningAmber
    else -> Icons.Rounded.Extension
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AetherExtensionWebView(
    node: JSONObject,
    extensionId: String,
    modifier: Modifier = Modifier,
) {
    val controller = LocalAetherExtensionUiController.current ?: return
    val html = node.optString("html")
    val url = node.optString("url")
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                webViewClient = WebViewClient()
                addJavascriptInterface(
                    AetherExtensionJavascriptBridge { action, args ->
                        controller.onAction(extensionId, action, args)
                    },
                    "Aether",
                )
                loadAetherExtensionWebContent(url, html)
            }
        },
        update = { webView ->
            val contentKey = "$url\u0000$html"
            if (webView.tag != contentKey) {
                webView.tag = contentKey
                webView.loadAetherExtensionWebContent(url, html)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(node.optDouble("height", 240.0).dp)
            .clip(RoundedCornerShape(node.optDouble("radius", 20.0).dp)),
    )
}

private fun WebView.loadAetherExtensionWebContent(
    url: String,
    html: String,
) {
    if (url.isNotBlank()) {
        loadUrl(url)
    } else {
        loadDataWithBaseURL(
            "https://aether.local/",
            html,
            "text/html",
            "UTF-8",
            null,
        )
    }
}

private class AetherExtensionJavascriptBridge(
    private val onAction: (String, JSONObject) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        val payload = runCatching { JSONObject(message) }.getOrNull() ?: return
        val action = payload.optString("action").trim()
        if (action.isBlank()) return
        onAction(action, payload.optJSONObject("args") ?: JSONObject())
    }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? {
    if (!has(name)) return null
    val value = opt(name)
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
