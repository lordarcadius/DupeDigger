package com.vipuljha.dupedigger.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class PaneMetrics(val width: Dp, val gutter: Dp, val actionsCompact: Boolean)

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
            Column(Modifier.fillMaxSize()) {
                Pane(paneMetrics(window), Modifier.weight(1f), content)
                AppNavBar(items, horizontalItems = window >= WindowBreakpoints.Medium)
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
private fun Pane(metrics: PaneMetrics, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.surface),
    ) { CompositionLocalProvider(LocalPaneMetrics provides metrics, content = content) }
}

/**
 * The flexible navigation bar. The active item is the filled icon with the pill indicator behind it;
 * every other item keeps its outlined icon, which is how M3 signals which destination is open.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppNavBar(items: ImmutableList<AppNavItem>, horizontalItems: Boolean) {
    ShortNavigationBar(
        arrangement = if (horizontalItems) ShortNavigationBarArrangement.Centered else ShortNavigationBarArrangement.EqualWeight,
    ) {
        items.forEach { item ->
            ShortNavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = { Icon(if (item.selected) item.selectedIcon else item.icon, contentDescription = null) },
                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                iconPosition = if (horizontalItems) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top,
            )
        }
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
                icon = { Icon(if (item.selected) item.selectedIcon else item.icon, contentDescription = null) },
                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

private val RailWidth = 96.dp
private val ActionsLabelMinWidth = 600.dp
