package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Immutable
data class PaneMetrics(val width: Dp, val gutter: Dp, val actionsCompact: Boolean, val drawerMode: Boolean)

val LocalPaneMetrics = compositionLocalOf {
    PaneMetrics(width = 0.dp, gutter = Dimens.gutterCompact, actionsCompact = true, drawerMode = true)
}

/**
 * The window shell: a `surfaceContainerHigh` field holding the navigation and a `surface` content pane.
 * - Below 600dp: modal drawer; [content] gets a non-null menu click.
 * - 600–839dp: 72dp icon rail.
 * - From 840dp: labelled panel the user resizes on its seam.
 */
@Composable
fun AppShell(
    savedPanelWidth: Dp?,
    onPanelWidthSaved: (Dp) -> Unit,
    drawerGesturesEnabled: Boolean,
    navPanel: @Composable (showLabels: Boolean, onNavigated: () -> Unit) -> Unit,
    content: @Composable (onMenuClick: (() -> Unit)?) -> Unit,
) {
    val field = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentBounds = LocalContentBounds.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(field)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        MeasureContentBounds(contentBounds)
        val window = maxWidth
        if (window < WindowBreakpoints.Medium) {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerGesturesEnabled,
                drawerContent = {
                    ModalDrawerSheet(drawerState, drawerContainerColor = field, modifier = Modifier.width(DrawerWidth)) {
                        navPanel(true) { scope.launch { drawerState.close() } }
                    }
                },
            ) {
                Pane(paneMetrics(window, drawerMode = true), rounded = false) { content { scope.launch { drawerState.open() } } }
            }
        } else {
            val expanded = window >= WindowBreakpoints.Expanded
            var liveWidth by remember { mutableStateOf<Dp?>(null) }
            val panelWidth = if (expanded) {
                (liveWidth ?: savedPanelWidth ?: PanelDefaultWidth).coerceIn(RailWidth, PanelMaxWidth)
            } else {
                RailWidth
            }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(panelWidth).fillMaxHeight()) {
                    navPanel(panelWidth >= PanelLabelsFrom) {}
                    if (expanded) {
                        PanelSeam(
                            onDrag = { delta -> liveWidth = ((liveWidth ?: panelWidth) + delta).coerceIn(RailWidth, PanelMaxWidth) },
                            onDragStopped = { liveWidth?.let(onPanelWidthSaved) },
                            onReset = { liveWidth = PanelDefaultWidth; onPanelWidthSaved(PanelDefaultWidth) },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
                Pane(paneMetrics(window - panelWidth, drawerMode = false), rounded = true, modifier = Modifier.weight(1f)) {
                    content(null)
                }
            }
        }
    }
}

private fun paneMetrics(width: Dp, drawerMode: Boolean) = PaneMetrics(
    width = width,
    gutter = if (width < WindowBreakpoints.Medium) Dimens.gutterCompact else Dimens.gutterMedium,
    actionsCompact = width < ActionsLabelMinWidth,
    drawerMode = drawerMode,
)

@Composable
private fun Pane(metrics: PaneMetrics, rounded: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = if (rounded) {
        MaterialTheme.shapes.large.copy(topEnd = ZeroCornerSize, bottomEnd = ZeroCornerSize, bottomStart = ZeroCornerSize)
    } else {
        RectangleShape
    }
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) { CompositionLocalProvider(LocalPaneMetrics provides metrics, content = content) }
}

/** The column shared by the panel, the rail and the drawer. */
@Composable
fun NavPanelColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(start = Dimens.spaceMd, end = Dimens.spaceMd, top = Dimens.spaceXl, bottom = Dimens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        content = content,
    )
}

@Composable
fun NavRow(icon: ImageVector, label: String, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val row = @Composable {
        Surface(
            selected = selected,
            onClick = onClick,
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Tab },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (showLabel) Arrangement.Start else Arrangement.Center,
                modifier = Modifier.heightIn(min = Dimens.touchTarget).padding(horizontal = Dimens.spaceMd),
            ) {
                Icon(icon, contentDescription = if (showLabel) null else label, modifier = Modifier.size(Dimens.icon))
                if (showLabel) {
                    Spacer(Modifier.width(Dimens.spaceMd))
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (showLabel) row() else PlainTooltipBox(label) { row() }
}

@Composable
private fun PanelSeam(onDrag: (Dp) -> Unit, onDragStopped: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = hovered || dragged
    val lineColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        tween(AppMotion.SeamMillis),
    )
    val lineWidth by animateDpAsState(if (active) SeamLineActive else SeamLineRest, tween(AppMotion.SeamMillis))
    val density = LocalDensity.current
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val currentOnReset by rememberUpdatedState(onReset)
    Box(
        modifier
            .fillMaxHeight()
            .width(SeamWidth)
            .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .hoverable(interaction)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { currentOnReset() }) }
            .draggable(
                state = rememberDraggableState { px -> onDrag(with(density) { (px * sign).toDp() }) },
                orientation = Orientation.Horizontal,
                interactionSource = interaction,
                onDragStopped = { onDragStopped() },
            ),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.fillMaxHeight().width(lineWidth).background(lineColor)) }
}

val SeamWidth = 8.dp
private val SeamLineRest = 1.dp
private val SeamLineActive = 3.dp
private val RailWidth = 72.dp
private val PanelDefaultWidth = 240.dp
private val PanelMaxWidth = 480.dp
private val PanelLabelsFrom = 100.dp
private val DrawerWidth = 240.dp
private val ActionsLabelMinWidth = 600.dp
