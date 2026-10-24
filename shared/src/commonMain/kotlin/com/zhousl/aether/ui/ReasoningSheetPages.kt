package com.zhousl.aether.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import com.zhousl.aether.ui.theme.AetherSurface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhousl.aether.platform.LocalReduceMotion
import com.zhousl.aether.shared.resources.Res
import com.zhousl.aether.shared.resources.common_back
import com.zhousl.aether.ui.theme.AetherOnSurface
import com.zhousl.aether.ui.theme.AetherOnSurfaceVariant
import org.jetbrains.compose.resources.stringResource

private val ReasoningFocusEasing = Easing { fraction ->
    // Fast entry, a slow middle, then a smooth acceleration out.
    val centered = 2f * fraction - 1f
    (centered * centered * centered + 1f) / 2f
}

private data class ReasoningDetailPage(val title: String, val sectionId: String)

/** Keeps summary navigation and scrolling inside the existing bottom sheet on both platforms. */
@Composable
fun ReasoningSheetPages(
    traceId: String,
    rawText: String,
    sections: List<ReasoningRawSection>,
    summaryContent: @Composable (onOpenRawReasoning: (String, String) -> Unit) -> Unit,
) {
    var selectedPage by remember(traceId) { mutableStateOf<ReasoningDetailPage?>(null) }
    val document = remember(rawText, sections) { reasoningRawDocument(rawText, sections) }
    val summaryScroll = rememberScrollState()
    val density = LocalDensity.current
    var measuredHeightPx by remember(traceId) { mutableStateOf(0) }
    var navigationHeightPx by remember(traceId) { mutableStateOf<Int?>(null) }
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = selectedPage,
        modifier = Modifier.fillMaxWidth()
            .then(navigationHeightPx?.let { heightPx ->
                Modifier.height(with(density) { heightPx.toDp() })
            } ?: Modifier.heightIn(max = 640.dp))
            .onSizeChanged { measuredHeightPx = it.height }
            .clipToBounds(),
        transitionSpec = {
            val forward = targetState != null
            val duration = if (reduceMotion) 0 else 280
            (slideInHorizontally(tween(duration)) { if (forward) it else -it } togetherWith
                slideOutHorizontally(tween(duration)) { if (forward) -it else it }).using(null)
        },
        label = "reasoning_detail_page",
    ) { page ->
        if (page == null) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(summaryScroll)
                    .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                summaryContent { title, sectionId ->
                    if (navigationHeightPx == null && measuredHeightPx > 0) {
                        navigationHeightPx = measuredHeightPx
                    }
                    selectedPage = ReasoningDetailPage(title, sectionId)
                }
            }
        } else {
            val rawScroll = rememberScrollState()
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val sweep = remember(page.sectionId) { Animatable(0f) }
            val range = document.ranges[page.sectionId]
            val highlightColor = if (AetherSurface.luminance() < 0.5f) {
                Color.White.copy(alpha = 0.10f)
            } else {
                Color.Black.copy(alpha = 0.075f)
            }
            LaunchedEffect(page.sectionId) {
                val layout = snapshotFlow { textLayout }.filterNotNull().first()
                if (range != null) {
                    // Let the horizontal page transition finish before moving through the document.
                    if (!reduceMotion) delay(280)
                    val top = layout.getBoundingBox(range.start).top.toInt()
                    rawScroll.animateScrollTo(
                        top.coerceIn(0, rawScroll.maxValue),
                        tween(if (reduceMotion) 0 else 3_000, easing = ReasoningFocusEasing),
                    )
                    if (!reduceMotion) {
                        sweep.animateTo(1f, keyframes {
                            durationMillis = 3_000
                            0f at 0 using CubicBezierEasing(0.22f, 0.84f, 0.18f, 1f)
                            0.5f at 750
                            0.5f at 2_250 using CubicBezierEasing(0.55f, 0f, 0.8f, 0.35f)
                            1f at 3_000
                        })
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedPage = null }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back),
                            tint = AetherOnSurfaceVariant,
                        )
                    }
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = AetherOnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SelectionContainer(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rawScroll)
                        .padding(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 30.dp),
                ) {
                    Text(
                        text = document.text,
                        onTextLayout = { textLayout = it },
                        modifier = Modifier.fillMaxWidth().drawBehind {
                            val layout = textLayout
                            if (layout != null && range != null && sweep.value > 0f && sweep.value < 1f) {
                                val path = layout.getPathForRange(range.start, range.end)
                                // Reveal the whole paragraph, hold it fully lit, then wipe it away to the right.
                                val left = ((sweep.value - 0.5f) * 2f).coerceIn(0f, 1f) * size.width
                                val right = (sweep.value * 2f).coerceIn(0f, 1f) * size.width
                                clipRect(left = left, right = right) {
                                    val firstLine = layout.getLineForOffset(range.start)
                                    val lastLine = layout.getLineForOffset(range.end - 1)
                                    val halfGap = 2.dp.toPx()
                                    for (line in firstLine..lastLine) {
                                        val lineTop = layout.getLineTop(line)
                                        val lineBottom = layout.getLineBottom(line)
                                        val inset = halfGap.coerceAtMost((lineBottom - lineTop) / 4f)
                                        clipRect(top = lineTop + inset, bottom = lineBottom - inset) {
                                            drawPath(path, highlightColor)
                                        }
                                    }
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AetherOnSurfaceVariant,
                    )
                }
            }
        }
    }
}
