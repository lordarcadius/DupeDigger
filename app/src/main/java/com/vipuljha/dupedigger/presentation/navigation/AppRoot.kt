package com.vipuljha.dupedigger.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.vipuljha.dupedigger.core.designsystem.AppNavItem
import com.vipuljha.dupedigger.core.designsystem.AppShell
import com.vipuljha.dupedigger.core.designsystem.LocalContentBounds
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.WindowBreakpoints
import com.vipuljha.dupedigger.core.designsystem.fadeThrough
import com.vipuljha.dupedigger.core.designsystem.popTransition
import com.vipuljha.dupedigger.core.designsystem.pushTransition
import com.vipuljha.dupedigger.presentation.ai.AiRoute
import com.vipuljha.dupedigger.presentation.compare.CompareRoute
import com.vipuljha.dupedigger.presentation.compare.compareViewModel
import com.vipuljha.dupedigger.presentation.group.GroupPlaceholder
import com.vipuljha.dupedigger.presentation.group.GroupRoute
import com.vipuljha.dupedigger.presentation.group.groupViewModel
import com.vipuljha.dupedigger.presentation.home.HomeRoute
import com.vipuljha.dupedigger.presentation.quarantine.QuarantineRoute
import com.vipuljha.dupedigger.presentation.results.ResultsRoute
import com.vipuljha.dupedigger.presentation.scan.ScanRoute
import com.vipuljha.dupedigger.presentation.settings.SettingsRoute
import kotlinx.collections.immutable.toImmutableList


/**
 * Hosts the shell and one back stack per top-level tab.
 * - Tab state and ViewModels survive switching, because each tab keeps its own decorated entries.
 * - Back pops the current tab, then returns to Home, then leaves the app.
 * - Reselecting a tab pops it to its root.
 * - From 600dp of pane width, Results and the open group share the pane.
 */
@Composable
fun AppRoot(shortcuts: AppShortcuts, modifier: Modifier = Modifier) {
    var currentTab by rememberSaveable { mutableStateOf(TopLevelTab.Home) }
    val backStacks = TopLevelTab.entries.associateWith { rememberNavBackStack(it.root) }
    val back: () -> Unit =
        { if (!backStacks.getValue(currentTab).back()) currentTab = TopLevelTab.Home }
    val selectTab: (TopLevelTab) -> Unit = { tab ->
        if (tab == currentTab) backStacks.getValue(tab).backTo(tab.root)
        currentTab = tab
    }

    /** Sets the tab directly: reselecting Home would pop the Scan screen it is about to show. */
    val openScan: () -> Unit = {
        val home = backStacks.getValue(TopLevelTab.Home)
        if (home.lastOrNull() != Destination.Scan) home.open(Destination.Scan)
        currentTab = TopLevelTab.Home
    }
    val onShortcut by rememberUpdatedState<(AppShortcut) -> Unit> { shortcut ->
        when {
            shortcut == AppShortcut.OpenScan -> openScan()
            shortcut == AppShortcut.Home -> selectTab(TopLevelTab.Home)
            shortcut == AppShortcut.Back -> back()
            else -> backStacks.getValue(currentTab).back()
        }
    }
    DisposableEffect(shortcuts) {
        shortcuts.handler = { onShortcut(it) }
        onDispose { shortcuts.handler = null }
    }
    val openGroup: (String) -> Unit = { fingerprint ->
        val results = backStacks.getValue(TopLevelTab.Results)
        if (results.lastOrNull() != Destination.Group(fingerprint)) {
            results.backTo(Destination.Results)
            results.open(Destination.Group(fingerprint))
        }
    }

    val topLevel = NavDisplay.transitionSpec { fadeThrough() } +
            NavDisplay.popTransitionSpec { fadeThrough() } +
            NavDisplay.predictivePopTransitionSpec { fadeThrough() }
    val provider = entryProvider<NavKey> {
        entry<Destination.Home>(metadata = topLevel) {
            HomeRoute(

                onOpenScan = { backStacks.getValue(TopLevelTab.Home).open(Destination.Scan) },
            )
        }
        entry<Destination.Scan> {
            ScanRoute(onBack = back, onViewResults = { selectTab(TopLevelTab.Results) })
        }
        entry<Destination.Results>(metadata = topLevel + paneRole(PaneRole.List)) {
            ResultsRoute(

                openGroup = (backStacks.getValue(TopLevelTab.Results)
                    .lastOrNull() as? Destination.Group)?.fingerprint,
                onOpenGroup = openGroup,
                onStartScan = openScan,
            )
        }
        entry<Destination.Group>(metadata = paneRole(PaneRole.Detail)) { key ->
            GroupRoute(
                onBack = back,
                onCompare = { left, right ->
                    backStacks.getValue(TopLevelTab.Results)
                        .open(Destination.Compare(key.fingerprint, left, right))
                },
                viewModel = groupViewModel(key.fingerprint),
            )
        }
        entry<Destination.Compare> { key ->
            CompareRoute(
                onBack = back,
                viewModel = compareViewModel(key.fingerprint, key.leftId, key.rightId)
            )
        }
        entry<Destination.Quarantine>(metadata = topLevel) {
            QuarantineRoute()
        }
        entry<Destination.Settings>(metadata = topLevel) {
            SettingsRoute(

                onOpenAi = {
                    backStacks.getValue(TopLevelTab.Settings).open(Destination.AiSettings)
                },
            )
        }
        entry<Destination.AiSettings> {
            AiRoute(onBack = back)
        }
    }
    val entriesByTab = TopLevelTab.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = backStacks.getValue(tab),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = provider,
        )
    }
    val start = entriesByTab.getValue(TopLevelTab.Home)
    val entries =
        if (currentTab == TopLevelTab.Home) start else start + entriesByTab.getValue(currentTab)

    val navItems = TopLevelTab.entries.map { tab ->
        AppNavItem(
            icon = tab.icon,
            selectedIcon = tab.selectedIcon,
            label = stringResource(tab.label),
            selected = tab == currentTab,
            onClick = { selectTab(tab) },
        )
    }.toImmutableList()

    CompositionLocalProvider(LocalContentBounds provides remember { mutableStateOf(IntRect.Zero) }) {
        AppShell(navItems) {
            val twoPane = LocalPaneMetrics.current.width >= WindowBreakpoints.Medium
            val sceneStrategy =
                remember(twoPane) { ListDetailSceneStrategy<NavKey>(twoPane) { GroupPlaceholder() } }
            NavDisplay(
                entries = entries,
                sceneStrategies = listOf(sceneStrategy),
                onBack = back,
                modifier = modifier,
                transitionSpec = { pushTransition() },
                popTransitionSpec = { popTransition() },
                predictivePopTransitionSpec = { popTransition() },
            )
        }
    }
}
