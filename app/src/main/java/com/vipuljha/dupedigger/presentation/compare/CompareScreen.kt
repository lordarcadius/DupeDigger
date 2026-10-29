package com.vipuljha.dupedigger.presentation.compare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppConnectedButtonGroup
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TierTag
import com.vipuljha.dupedigger.core.designsystem.TooltipIconButton
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.designsystem.WindowBreakpoints
import com.vipuljha.dupedigger.core.ui.StateSwitch
import com.vipuljha.dupedigger.core.ui.asString
import kotlinx.collections.immutable.persistentListOf

private enum class CompareMode { SideBySide, Slider }

@Composable
fun CompareScreen(state: CompareState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onBack = onBack) { TopBarTitle(stringResource(R.string.compare_title)) } },
    ) { padding ->
        StateSwitch(
            state = state.content,
            isEmpty = { it == null },
            modifier = Modifier.fillMaxSize().padding(padding),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() } },
            empty = {
                StateMessage(
                    icon = Icons.Outlined.FolderOff,
                    title = stringResource(R.string.group_gone_title),
                    subtitle = stringResource(R.string.group_gone_body),
                    action = { AppButton(stringResource(R.string.action_back), onBack) },
                )
            },
            error = { error ->
                StateMessage(Icons.Outlined.ErrorOutline, stringResource(R.string.group_error_title), error.message.asString(), isError = true)
            },
        ) { compare -> if (compare != null) Comparison(compare) }
    }
}

/**
 * Both images share one zoom and pan: pinch, the mouse wheel and the zoom buttons zoom, dragging pans, a double tap
 * resets. The slider mode stacks them and the divider follows the slider below.
 */
@Composable
private fun Comparison(compare: CompareUi) {
    var mode by rememberSaveable { mutableStateOf(CompareMode.SideBySide) }
    var divider by rememberSaveable { mutableFloatStateOf(0.5f) }
    val zoom = remember { ZoomState() }
    val gutter = LocalPaneMetrics.current.gutter
    Column(Modifier.fillMaxSize().padding(horizontal = gutter)) {
        compare.similarity?.let { TierTag(it.asString(), Modifier.padding(bottom = Dimens.spaceSm)) }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().zoomGestures(zoom)) {
            when (mode) {
                CompareMode.SideBySide -> SideBySide(compare, zoom)
                CompareMode.Slider -> Wipe(compare, zoom, divider)
            }
        }
        if (mode == CompareMode.Slider) {
            val label = stringResource(R.string.compare_divider)
            Slider(divider, { divider = it }, Modifier.fillMaxWidth().semantics { contentDescription = label })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.spaceSm), verticalAlignment = Alignment.CenterVertically) {
            AppConnectedButtonGroup(
                options = persistentListOf(CompareMode.SideBySide, CompareMode.Slider),
                isSelected = { it == mode },
                onClick = { mode = it },
                label = { stringResource(if (it == CompareMode.SideBySide) R.string.compare_mode_side else R.string.compare_mode_slider) },
                modifier = Modifier.weight(1f).widthIn(max = ModeGroupMaxWidth),
            )
            TooltipIconButton(Icons.Filled.ZoomOut, stringResource(R.string.compare_zoom_out), { zoom.zoomBy(1 / ZOOM_STEP) }, enabled = zoom.scale > 1f)
            TooltipIconButton(Icons.Filled.ZoomIn, stringResource(R.string.compare_zoom_in), { zoom.zoomBy(ZOOM_STEP) }, enabled = zoom.scale < MAX_SCALE)
            TooltipIconButton(Icons.Filled.CenterFocusStrong, stringResource(R.string.compare_zoom_reset), zoom::reset, enabled = zoom.scale > 1f)
        }
    }
}

@Composable
private fun SideBySide(compare: CompareUi, zoom: ZoomState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panes = listOf(compare.left, compare.right)
        if (maxWidth < WindowBreakpoints.Medium) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                panes.forEach { Pane(it, zoom, Modifier.weight(1f)) }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                panes.forEach { Pane(it, zoom, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Pane(pane: ComparePaneUi, zoom: ZoomState, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { ZoomedImage(pane, zoom, Modifier.fillMaxSize()) }
        PaneCaption(pane)
    }
}

@Composable
private fun Wipe(compare: CompareUi, zoom: ZoomState, divider: Float) {
    val line = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ZoomedImage(compare.right, zoom, Modifier.fillMaxSize())
            ZoomedImage(
                compare.left,
                zoom,
                Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * divider) { this@drawWithContent.drawContent() } },
            )
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * divider
                drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = DividerWidth.toPx())
            }
        }
        Row(Modifier.fillMaxWidth()) {
            PaneCaption(compare.left, Modifier.weight(1f))
            PaneCaption(compare.right, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PaneCaption(pane: ComparePaneUi, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = Dimens.spaceXs)) {
        Text(pane.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pane.details.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Decoded larger than the view so a zoomed-in detail stays sharp. */
@Composable
private fun ZoomedImage(pane: ComparePaneUi, zoom: ZoomState, modifier: Modifier) {
    val context = LocalPlatformContext.current
    val request = remember(pane.uri) { ImageRequest.Builder(context).data(pane.uri).size(DECODE_SIZE).build() }
    AsyncImage(
        model = request,
        contentDescription = pane.name,
        placeholder = painterResource(R.drawable.art_thumb_placeholder),
        error = painterResource(R.drawable.art_thumb_broken),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { zoom.viewSize = it }
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            },
    )
}

private fun Modifier.zoomGestures(zoom: ZoomState): Modifier = this
    .pointerInput(zoom) { detectTransformGestures { _, pan, factor, _ -> zoom.transform(pan, factor) } }
    .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.reset() }) }
    .pointerInput(zoom) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val delta = event.changes.first().scrollDelta.y
                    if (delta != 0f) zoom.zoomBy(if (delta < 0) WHEEL_STEP else 1 / WHEEL_STEP)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }

/** One zoom and pan shared by both images; panning stops at the image's edges. */
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewSize = IntSize.Zero

    fun zoomBy(factor: Float) = transform(Offset.Zero, factor)

    fun transform(pan: Offset, factor: Float) {
        scale = (scale * factor).coerceIn(1f, MAX_SCALE)
        val maxX = viewSize.width * (scale - 1) / 2
        val maxY = viewSize.height * (scale - 1) / 2
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

private const val MAX_SCALE = 8f
private const val ZOOM_STEP = 1.5f
private const val WHEEL_STEP = 1.15f
private const val DECODE_SIZE = 2048
private val DividerWidth = Dimens.spaceXxs
private val ModeGroupMaxWidth = Dimens.maxWidthDetailActions
