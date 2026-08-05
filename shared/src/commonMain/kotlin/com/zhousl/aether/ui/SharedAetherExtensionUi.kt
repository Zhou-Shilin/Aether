package com.zhousl.aether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.zhousl.aether.data.SharedAetherExtensionPage
import com.zhousl.aether.data.SharedAetherExtensionSnapshot
import com.zhousl.aether.platform.PlatformWebView
import com.zhousl.aether.ui.theme.AetherBackground
import com.zhousl.aether.ui.theme.AetherOnPrimary
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import com.zhousl.aether.ui.theme.AetherPrimary
import com.zhousl.aether.ui.theme.AetherSurface
import com.zhousl.aether.ui.theme.AetherSurfaceHigh
import com.zhousl.aether.ui.theme.AetherSurfaceHigher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

const val SharedExtensionSlotAppOverlay = "app.overlay"
const val SharedExtensionSlotChatTop = "chat.top"
const val SharedExtensionSlotChatEmpty = "chat.empty"
const val SharedExtensionSlotChatListStart = "chat.list.start"
const val SharedExtensionSlotChatListEnd = "chat.list.end"
const val SharedExtensionSlotChatComposerTop = "chat.composer.top"
const val SharedExtensionSlotSettingsHub = "settings.hub"
const val SharedExtensionSlotDrawer = "drawer"
const val SharedExtensionSlotDrawerHeader = "drawer.header"
const val SharedExtensionSlotDrawerFooter = "drawer.footer"
const val SharedExtensionSlotDrawerListEnd = "drawer.list.end"

@Immutable
data class SharedAetherExtensionUiController(
    val snapshot: SharedAetherExtensionSnapshot,
    val onAction: (String, String, JsonObject) -> Unit,
    val onOpenPage: (String) -> Unit,
)

val LocalSharedAetherExtensionUiController =
    staticCompositionLocalOf<SharedAetherExtensionUiController?> { null }

@Composable
fun SharedAetherExtensionUiProvider(
    controller: SharedAetherExtensionUiController,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSharedAetherExtensionUiController provides controller, content = content)
}

@Composable
fun SharedAetherExtensionSlot(
    slot: String,
    modifier: Modifier = Modifier,
) {
    val controller = LocalSharedAetherExtensionUiController.current ?: return
    val surfaces = controller.snapshot.surfacesAt(slot)
    if (surfaces.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        surfaces.forEach { surface ->
            key(surface.id) {
                SharedAetherExtensionView(surface.tree, surface.extensionId)
            }
        }
    }
}

@Composable
fun SharedAetherExtensionOverlay(modifier: Modifier = Modifier) {
    val controller = LocalSharedAetherExtensionUiController.current ?: return
    Box(modifier) {
        controller.snapshot.surfacesAt(SharedExtensionSlotAppOverlay).forEach { surface ->
            key(surface.id) { SharedAetherExtensionView(surface.tree, surface.extensionId) }
        }
    }
}

@Composable
fun SharedAetherExtensionComponentHost(
    target: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val controller = LocalSharedAetherExtensionUiController.current
    val components = controller?.snapshot?.componentsAt(target).orEmpty()
    if (components.isEmpty()) {
        content()
        return
    }
    val before = components.filter { it.mode.equals("before", true) }
    val after = components.filter { it.mode.equals("after", true) }
    val decisive = components.lastOrNull {
        it.mode.equals("replace", true) || it.mode.equals("hide", true)
    }
    var center: @Composable () -> Unit = when {
        decisive?.mode.equals("hide", true) -> ({})
        decisive?.mode.equals("replace", true) -> ({
            SharedAetherExtensionView(decisive?.tree, decisive?.extensionId.orEmpty())
        })
        else -> content
    }
    components.filter { it.mode.equals("wrap", true) }.forEach { component ->
        val nested = center
        center = {
            SharedAetherExtensionView(
                component.tree,
                component.extensionId,
                nativeContent = nested,
            )
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        before.forEach { SharedAetherExtensionView(it.tree, it.extensionId) }
        center()
        after.forEach { SharedAetherExtensionView(it.tree, it.extensionId) }
    }
}

@Composable
fun SharedAetherExtensionPageScreen(
    page: SharedAetherExtensionPage,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = AetherBackground,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(AetherBackground)
                    .statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = AetherOnSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(page.title, style = MaterialTheme.typography.titleLarge, color = AetherOnSurface)
                    if (page.subtitle.isNotBlank()) {
                        Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            SharedAetherExtensionView(page.tree, page.extensionId, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SharedAetherExtensionPageLauncher(
    page: SharedAetherExtensionPage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(AetherSurfaceHigh).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(AetherSurfaceHigher),
            contentAlignment = Alignment.Center,
        ) {
            Icon(extensionIcon(page.icon), null, tint = AetherOnSurface, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(page.title, style = MaterialTheme.typography.bodyLarge, color = AetherOnSurface)
            if (page.subtitle.isNotBlank()) {
                Text(page.subtitle, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SharedAetherExtensionView(
    value: JsonElement?,
    extensionId: String,
    modifier: Modifier = Modifier,
    nativeContent: (@Composable () -> Unit)? = null,
) {
    when (value) {
        null, JsonNull -> Unit
        is JsonArray -> Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            value.forEachIndexed { index, child ->
                key(index) { SharedAetherExtensionView(child, extensionId, nativeContent = nativeContent) }
            }
        }
        is JsonObject -> SharedAetherExtensionNode(value, extensionId, modifier, nativeContent)
        is JsonPrimitive -> Text(
            value.contentOrNull.orEmpty(),
            modifier = modifier,
            color = AetherOnSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SharedAetherExtensionNode(
    node: JsonObject,
    extensionId: String,
    modifier: Modifier,
    nativeContent: (@Composable () -> Unit)?,
) {
    val controller = LocalSharedAetherExtensionUiController.current ?: return
    val type = node.string("type").ifBlank { "column" }.lowercase()
    val resolvedModifier = nodeModifier(modifier, node)
    val action = node.string("action").trim()
    val args = node["args"] as? JsonObject ?: JsonObject(emptyMap())
    val clickable = if (action.isNotBlank()) {
        resolvedModifier.clickable { controller.onAction(extensionId, action, args) }
    } else resolvedModifier

    when (type) {
        "text", "code" -> Text(
            text = node.string("text"),
            modifier = clickable,
            color = node.string("color").takeIf(String::isNotBlank)?.let { extensionColor(it) }
                ?: AetherOnSurface,
            style = extensionTextStyle(node, type == "code"),
            fontWeight = extensionFontWeight(node.string("weight")),
            textAlign = extensionTextAlign(node.string("align")),
            maxLines = (node.int("maxLines") ?: Int.MAX_VALUE).coerceAtLeast(1),
            overflow = TextOverflow.Ellipsis,
        )
        "row" -> Row(
            modifier = if ("width" in node) clickable else clickable.fillMaxWidth(),
            horizontalArrangement = extensionHorizontalArrangement(node.string("arrangement")),
            verticalAlignment = extensionVerticalAlignment(node.string("verticalAlignment")),
        ) {
            renderRowChildren(node["children"] as? JsonArray, extensionId, nativeContent)
        }
        "box" -> Box(clickable, contentAlignment = extensionBoxAlignment(node.string("alignment"))) {
            renderChildren(node["children"] as? JsonArray, extensionId, nativeContent)
        }
        "card" -> Column(
            modifier = clickable.fillMaxWidth()
                .clip(RoundedCornerShape(node.double("radius", 8.0).dp))
                .background(cardColor(node.string("tone")))
                .padding(node.double("contentPadding", 16.0).dp),
            verticalArrangement = Arrangement.spacedBy(node.double("spacing", 8.0).dp),
        ) { renderChildren(node["children"] as? JsonArray, extensionId, nativeContent) }
        "scroll", "column" -> Column(
            modifier = if (node.boolean("scroll") || type == "scroll") {
                clickable.verticalScroll(rememberScrollState())
            } else clickable,
            verticalArrangement = extensionVerticalArrangement(node.string("arrangement")),
            horizontalAlignment = extensionHorizontalAlignment(node.string("horizontalAlignment")),
        ) { renderChildren(node["children"] as? JsonArray, extensionId, nativeContent) }
        "core", "next" -> Box(resolvedModifier) { nativeContent?.invoke() }
        "button" -> Button(
            onClick = { if (action.isNotBlank()) controller.onAction(extensionId, action, args) },
            enabled = node.boolean("enabled", true),
            modifier = resolvedModifier,
            shape = RoundedCornerShape(node.double("radius", 8.0).dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (node.string("tone")) {
                    "neutral", "secondary" -> AetherSurfaceHigher
                    "danger", "error" -> MaterialTheme.colorScheme.errorContainer
                    else -> AetherPrimary
                },
                contentColor = when (node.string("tone")) {
                    "neutral", "secondary" -> AetherOnSurface
                    "danger", "error" -> MaterialTheme.colorScheme.onErrorContainer
                    else -> AetherOnPrimary
                },
            ),
        ) {
            node.string("icon").takeIf(String::isNotBlank)?.let {
                Icon(extensionIcon(it), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(node.string("label").ifBlank { node.string("text") }, maxLines = 1)
        }
        "iconbutton" -> IconButton(
            onClick = { if (action.isNotBlank()) controller.onAction(extensionId, action, args) },
            enabled = node.boolean("enabled", true),
            modifier = resolvedModifier.clip(CircleShape).background(AetherSurfaceHigh),
        ) {
            Icon(extensionIcon(node.string("icon")), node.string("contentDescription").ifBlank { null })
        }
        "switch" -> {
            val checked = node.boolean("checked")
            val dispatch: (Boolean) -> Unit = { next ->
                controller.onAction(extensionId, action, JsonObject(args + ("checked" to JsonPrimitive(next))))
            }
            Row(
                modifier = resolvedModifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(AetherSurfaceHigh).clickable(enabled = action.isNotBlank()) { dispatch(!checked) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(node.string("label"), color = AetherOnSurface)
                    node.string("subtitle").takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = AetherOnSurfaceVariant)
                    }
                }
                Switch(
                    checked = checked,
                    onCheckedChange = if (action.isBlank()) null else dispatch,
                    colors = SwitchDefaults.colors(checkedTrackColor = AetherPrimary),
                )
            }
        }
        "input" -> {
            val externalValue = node.string("value")
            var value by remember(node.string("id"), externalValue) { mutableStateOf(externalValue) }
            val submit = {
                if (action.isNotBlank()) {
                    controller.onAction(
                        extensionId,
                        action,
                        JsonObject(args + ("value" to JsonPrimitive(value))),
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    if (node.string("dispatch") == "change") submit()
                },
                modifier = resolvedModifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(AetherSurfaceHigh).padding(horizontal = 14.dp, vertical = 12.dp),
                enabled = node.boolean("enabled", true),
                singleLine = node.boolean("singleLine", true),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AetherOnSurface),
                cursorBrush = SolidColor(AetherPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(node.string("placeholder"), color = AetherOnSurfaceVariant)
                        }
                        field()
                    }
                },
            )
        }
        "spacer" -> Spacer(
            Modifier.height(node.double("height", node.double("size", 8.0)).dp)
                .width(node.double("width", node.double("size", 8.0)).dp)
        )
        "progress" -> {
            val progress = node["value"]?.jsonPrimitive?.doubleOrNull
            if (progress == null) {
                CircularProgressIndicator(
                    modifier = resolvedModifier.size(node.double("size", 24.0).dp),
                    strokeWidth = node.double("strokeWidth", 2.0).dp,
                    color = AetherPrimary,
                )
            } else {
                CircularProgressIndicator(
                    progress = { progress.toFloat().coerceIn(0f, 1f) },
                    modifier = resolvedModifier.size(node.double("size", 24.0).dp),
                    strokeWidth = node.double("strokeWidth", 2.0).dp,
                    color = AetherPrimary,
                )
            }
        }
        "web" -> PlatformWebView(
            url = node.string("url"),
            html = node.string("html"),
            onMessage = { message ->
                val payload = runCatching {
                    Json.parseToJsonElement(message) as? JsonObject
                }.getOrNull()
                val webAction = payload?.string("action").orEmpty()
                if (webAction.isNotBlank()) {
                    controller.onAction(
                        extensionId,
                        webAction,
                        payload?.get("args") as? JsonObject ?: JsonObject(emptyMap()),
                    )
                }
            },
            modifier = resolvedModifier.fillMaxWidth()
                .height(node.double("height", 240.0).dp)
                .clip(RoundedCornerShape(node.double("radius", 8.0).dp)),
        )
        "pagebutton" -> {
            val localPage = node.string("page")
            Button(
                onClick = {
                    controller.onOpenPage(
                        if (':' in localPage) localPage else extensionId + ":" + localPage
                    )
                },
                modifier = resolvedModifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AetherSurfaceHigher,
                    contentColor = AetherOnSurface,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(extensionIcon(node.string("icon")), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(node.string("label"))
            }
        }
        else -> Column(clickable, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            renderChildren(node["children"] as? JsonArray, extensionId, nativeContent)
        }
    }
}

@Composable
private fun renderChildren(
    children: JsonArray?,
    extensionId: String,
    nativeContent: (@Composable () -> Unit)?,
) {
    children.orEmpty().forEachIndexed { index, child ->
        key(index) { SharedAetherExtensionView(child, extensionId, nativeContent = nativeContent) }
    }
}

@Composable
private fun RowScope.renderRowChildren(
    children: JsonArray?,
    extensionId: String,
    nativeContent: (@Composable () -> Unit)?,
) {
    children.orEmpty().forEachIndexed { index, child ->
        key(index) {
            val weight = (child as? JsonObject)?.doubleOrNull("weight")?.toFloat()?.takeIf { it > 0 }
            SharedAetherExtensionView(
                child,
                extensionId,
                modifier = if (weight == null) Modifier else Modifier.weight(weight),
                nativeContent = nativeContent,
            )
        }
    }
}

@Composable
private fun nodeModifier(base: Modifier, node: JsonObject): Modifier {
    var modifier = base
    when (node.string("width")) {
        "fill", "match" -> modifier = modifier.fillMaxWidth()
        "full" -> modifier = modifier.fillMaxSize()
        else -> node.doubleOrNull("width")?.let { modifier = modifier.width(it.dp) }
    }
    when (node.string("height")) {
        "fill", "match" -> modifier = modifier.fillMaxHeight()
        "full" -> modifier = modifier.fillMaxSize()
        else -> node.doubleOrNull("height")?.let { modifier = modifier.height(it.dp) }
    }
    node.doubleOrNull("minHeight")?.let { modifier = modifier.heightIn(min = it.dp) }
    node.doubleOrNull("maxHeight")?.let { modifier = modifier.heightIn(max = it.dp) }
    node.doubleOrNull("alpha")?.let { modifier = modifier.alpha(it.toFloat().coerceIn(0f, 1f)) }
    val background = node.string("background")
    if (background.isNotBlank()) {
        modifier = modifier.clip(RoundedCornerShape(node.double("radius", 0.0).dp))
            .background(extensionColor(background))
    }
    when (val padding = node["padding"]) {
        is JsonPrimitive -> padding.doubleOrNull?.let { modifier = modifier.padding(it.dp) }
        is JsonObject -> modifier = modifier.padding(
            start = padding.double("start", padding.double("horizontal", 0.0)).dp,
            top = padding.double("top", padding.double("vertical", 0.0)).dp,
            end = padding.double("end", padding.double("horizontal", 0.0)).dp,
            bottom = padding.double("bottom", padding.double("vertical", 0.0)).dp,
        )
        else -> Unit
    }
    if (node.boolean("horizontalScroll")) modifier = modifier.horizontalScroll(rememberScrollState())
    return modifier
}

@Composable
private fun extensionTextStyle(node: JsonObject, code: Boolean) =
    when (node.string("style").lowercase()) {
        "display" -> MaterialTheme.typography.displaySmall
        "headline" -> MaterialTheme.typography.headlineSmall
        "title" -> MaterialTheme.typography.titleLarge
        "subtitle" -> MaterialTheme.typography.titleMedium
        "label" -> MaterialTheme.typography.labelLarge
        "caption", "small" -> MaterialTheme.typography.bodySmall
        else -> MaterialTheme.typography.bodyMedium
    }.let { style ->
        val sized = node.doubleOrNull("fontSize")?.let { style.copy(fontSize = it.sp) } ?: style
        if (code || node.boolean("monospace")) sized.copy(fontFamily = FontFamily.Monospace) else sized
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

private fun parseHexColor(value: String): Color? = runCatching {
    val raw = value.removePrefix("#")
    val argb = when (raw.length) {
        6 -> (0xFF000000u or raw.toUInt(16)).toULong()
        8 -> raw.toULong(16)
        else -> return null
    }
    Color(argb)
}.getOrNull()

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

private fun JsonObject.string(name: String): String =
    get(name)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(name: String): Int? =
    get(name)?.jsonPrimitive?.intOrNull

private fun JsonObject.boolean(name: String, fallback: Boolean = false): Boolean =
    get(name)?.jsonPrimitive?.booleanOrNull ?: fallback

private fun JsonObject.double(name: String, fallback: Double): Double =
    doubleOrNull(name) ?: fallback

private fun JsonObject.doubleOrNull(name: String): Double? =
    get(name)?.jsonPrimitive?.doubleOrNull
