package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class PaneMetrics(
    val width: Dp,
    val gutter: Dp,
    val actionsCompact: Boolean,
    /** How much of the pane's bottom the floating bar covers; scrollables add it so nothing hides behind it. */
    val bottomOverlay: Dp = 0.dp,
)

val LocalPaneMetrics = compositionLocalOf {
    PaneMetrics(width = 0.dp, gutter = Dimens.gutterCompact, actionsCompact = true)
}

/** One top-level destination. [selectedIcon] is the filled form M3 uses to mark the active item. */
@Immutable
data class AppNavItem(
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The window shell, following M3 Expressive's navigation rules.
 * - Compact (under 600dp): flexible navigation bar at the bottom, items stacked icon over label.
 * - Medium (600–839dp): the same bar, centred, with the icon beside the label.
 * - Expanded (840dp and up): a wide navigation rail down the side, because a navigation bar is for
 *   compact and medium windows only.
 *
 * There is no navigation drawer: M3 Expressive no longer recommends one and names the expanded
 * navigation rail as its replacement. The body area is `surface` and the navigation area is
 * `surface container`, the mapping M3 keeps constant across every window size.
 */
@Composable
fun AppShell(items: ImmutableList<AppNavItem>, content: @Composable () -> Unit) {
    val contentBounds = LocalContentBounds.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        MeasureContentBounds(contentBounds)
        val window = maxWidth
        if (window >= WindowBreakpoints.Expanded) {
            Row(Modifier.fillMaxSize()) {
                AppNavRail(items)
                Pane(paneMetrics(window - RailWidth), Modifier.weight(1f), content)
            }
        } else {
            // The bar floats over the content, so the content is recorded into a layer the bar blurs.
            val backdrop = rememberGraphicsLayer()
            val barInsets = WindowInsets.navigationBars.asPaddingValues()
            val metrics = paneMetrics(window).copy(
                bottomOverlay = FloatingBarHeight + FloatingBarInset * 2 + barInsets.calculateBottomPadding(),
            )
            Box(Modifier.fillMaxSize()) {
                Pane(
                    metrics,
                    Modifier.drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    },
                    content,
                )
                AppNavBar(
                    items = items,
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private fun paneMetrics(width: Dp) = PaneMetrics(
    width = width,
    gutter = if (width < WindowBreakpoints.Medium) Dimens.gutterCompact else Dimens.gutterMedium,
    actionsCompact = width < ActionsLabelMinWidth,
)

@Composable
private fun Pane(
    metrics: PaneMetrics,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.surface),
    ) { CompositionLocalProvider(LocalPaneMetrics provides metrics, content = content) }
}

/**
 * The navigation bar as a floating glass pill: fully rounded, inset from the window, lifted off the
 * content, and blurring whatever scrolls underneath it.
 *
 * **From M3:** the items are the flexible navigation bar's, so the active destination is the filled icon
 * inside the active indicator and every other one stays outlined. Elevation is level 2 (3dp), M3's
 * resting level for a toolbar. The tint over the blur is `surface container`, M3's navigation role.
 *
 * **My design decisions (not in M3):** the floating container, the 16dp inset, the blur and its radius,
 * and dropping the labels. M3's navigation bar is square and full-width, it documents this rounded
 * floating shape as the *floating toolbar* (a component for actions), and it says plainly not to remove
 * navigation labels. All of that is a deliberate departure the owner asked for, written up in D-025.
 * Because the labels are gone, each icon carries its destination name for assistive tech instead.
 */
@Composable
private fun AppNavBar(
    items: ImmutableList<AppNavItem>,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val blurRadius = with(LocalDensity.current) { FloatingBarBlur.toPx() }
    val glass = rememberGraphicsLayer().apply {
        renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = GlassTintAlpha)
    val indicator = MaterialTheme.colorScheme.onSurface.copy(alpha = IndicatorAlpha)
    val selected = items.indexOfFirst { it.selected }.coerceAtLeast(0)
    // While a finger is down the highlight follows it, so a drag across the bar previews each destination.
    var pressed by remember { mutableStateOf<Int?>(null) }
    val highlighted = pressed ?: selected

    BoxWithConstraints(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(FloatingBarInset)
            .fillMaxWidth()
            .height(FloatingBarHeight)
            .clip(CircleShape)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                // Re-blur the content recorded behind the bar each frame, so it tracks scrolling live.
                glass.record(size.toIntSize()) {
                    translate(-origin.x, -origin.y) { drawLayer(backdrop) }
                }
                drawLayer(glass)
                drawRect(tint)
            }
            .pointerInput(items) {
                val cell = size.width.toFloat() / items.size
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = (down.position.x / cell).toInt().coerceIn(items.indices)
                    while (true) {
                        val change =
                            awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        pressed = (change.position.x / cell).toInt().coerceIn(items.indices)
                    }
                    pressed?.let { items[it].onClick() }
                    pressed = null
                }
            },
    ) {
        val cell = maxWidth / items.size
        val slide by animateDpAsState(cell * highlighted, listSlideSpec(), label = "navIndicator")
        Box(
            Modifier
                .offset(x = slide)
                .width(cell)
                .fillMaxHeight()
                .padding(IndicatorInset)
                .clip(CircleShape)
                .background(indicator),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                NavIcon(item, highlighted == index, Modifier
                    .weight(1f)
                    .fillMaxHeight())
            }
        }
    }
}

/**
 * One destination. The indicator is large — the reference fills about 93% of the bar's height with it —
 * and deliberately low-contrast: it is a tint of `on surface`, not an accent fill. That is what lets a
 * shape this big sit quietly behind the icon. The selected state is carried by the filled icon and its
 * stronger tint, so it survives the quiet indicator.
 */
@Composable
private fun NavIcon(item: AppNavItem, highlighted: Boolean, modifier: Modifier = Modifier) {
    // The bar owns the pointer input so a drag can cross items, so each one carries only its semantics.
    Box(
        modifier.semantics(mergeDescendants = true) {
            role = Role.Tab
            selected = item.selected
            contentDescription = item.label
            onClick { item.onClick(); true }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (highlighted) item.selectedIcon else item.icon,
            contentDescription = null,
            tint = if (highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(NavIconSize),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavRail(items: ImmutableList<AppNavItem>) {
    WideNavigationRail(arrangement = Arrangement.Center) {
        items.forEach { item ->
            WideNavigationRailItem(
                railExpanded = false,
                selected = item.selected,
                onClick = item.onClick,
                icon = {
                    Icon(
                        if (item.selected) item.selectedIcon else item.icon,
                        contentDescription = null
                    )
                },
                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

private val RailWidth = 96.dp

// Not published by M3 for this use: the gap the floating bar keeps from the window, and its resting lift.
private val FloatingBarInset = 16.dp
private val FloatingBarHeight = 72.dp
private val NavIconSize = 28.dp

// Proportions taken from the reference: the indicator is 0.83x the bar's height and a little wider than tall.
// Uniform on all four sides, so at the ends the indicator's curve nests inside the container's own.
private val IndicatorInset = 4.dp
private const val IndicatorAlpha = 0.10f
private val FloatingBarBlur = 24.dp
private const val GlassTintAlpha = 0.55f
private val ActionsLabelMinWidth = 600.dp
