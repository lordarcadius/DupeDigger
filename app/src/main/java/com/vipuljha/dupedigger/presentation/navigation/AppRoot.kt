package com.vipuljha.dupedigger.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.vipuljha.dupedigger.core.designsystem.AppShell
import com.vipuljha.dupedigger.core.designsystem.LocalContentBounds
import com.vipuljha.dupedigger.core.designsystem.NavPanelColumn
import com.vipuljha.dupedigger.core.designsystem.NavRow
import com.vipuljha.dupedigger.core.designsystem.fadeThrough
import com.vipuljha.dupedigger.core.designsystem.popTransition
import com.vipuljha.dupedigger.core.designsystem.pushTransition
import com.vipuljha.dupedigger.presentation.ai.AiRoute
import com.vipuljha.dupedigger.presentation.compare.CompareRoute
import com.vipuljha.dupedigger.presentation.compare.compareViewModel
import com.vipuljha.dupedigger.presentation.group.GroupRoute
import com.vipuljha.dupedigger.presentation.group.groupViewModel
import com.vipuljha.dupedigger.presentation.home.HomeRoute
import com.vipuljha.dupedigger.presentation.quarantine.QuarantineRoute
import com.vipuljha.dupedigger.presentation.results.ResultsRoute
import com.vipuljha.dupedigger.presentation.scan.ScanRoute
import com.vipuljha.dupedigger.presentation.settings.SettingsRoute

val LocalMenuClick = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Hosts the shell and one back stack per top-level tab.
 * - Tab state and ViewModels survive switching, because each tab keeps its own decorated entries.
 * - Back pops the current tab, then returns to Home, then leaves the app.
 * - Reselecting a tab pops it to its root.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    var currentTab by rememberSaveable { mutableStateOf(TopLevelTab.Home) }
    var savedPanelWidthDp by rememberSaveable { mutableStateOf<Float?>(null) }
    val backStacks = TopLevelTab.entries.associateWith { rememberNavBackStack(it.root) }
    val back: () -> Unit = { if (!backStacks.getValue(currentTab).back()) currentTab = TopLevelTab.Home }
    val selectTab: (TopLevelTab) -> Unit = { tab ->
        if (tab == currentTab) backStacks.getValue(tab).backTo(tab.root)
        currentTab = tab
    }

    val topLevel = NavDisplay.transitionSpec { fadeThrough() } +
        NavDisplay.popTransitionSpec { fadeThrough() } +
        NavDisplay.predictivePopTransitionSpec { fadeThrough() }
    val provider = entryProvider<NavKey> {
        entry<Destination.Home>(metadata = topLevel) {
            HomeRoute(
                onMenuClick = LocalMenuClick.current,
                onOpenScan = { backStacks.getValue(TopLevelTab.Home).open(Destination.Scan) },
            )
        }
        entry<Destination.Scan> {
            ScanRoute(onBack = back, onViewResults = { selectTab(TopLevelTab.Results) })
        }
        entry<Destination.Results>(metadata = topLevel) {
            ResultsRoute(
                onMenuClick = LocalMenuClick.current,
                onOpenGroup = { backStacks.getValue(TopLevelTab.Results).open(Destination.Group(it)) },
                onStartScan = {
                    val home = backStacks.getValue(TopLevelTab.Home)
                    if (home.lastOrNull() != Destination.Scan) home.open(Destination.Scan)
                    selectTab(TopLevelTab.Home)
                },
            )
        }
        entry<Destination.Group> { key ->
            GroupRoute(
                onBack = back,
                onCompare = { left, right -> backStacks.getValue(TopLevelTab.Results).open(Destination.Compare(key.fingerprint, left, right)) },
                viewModel = groupViewModel(key.fingerprint),
            )
        }
        entry<Destination.Compare> { key ->
            CompareRoute(onBack = back, viewModel = compareViewModel(key.fingerprint, key.leftId, key.rightId))
        }
        entry<Destination.Quarantine>(metadata = topLevel) {
            QuarantineRoute(onMenuClick = LocalMenuClick.current)
        }
        entry<Destination.Settings>(metadata = topLevel) {
            SettingsRoute(
                onMenuClick = LocalMenuClick.current,
                onOpenAi = { backStacks.getValue(TopLevelTab.Settings).open(Destination.AiSettings) },
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
    val entries = if (currentTab == TopLevelTab.Home) start else start + entriesByTab.getValue(currentTab)

    CompositionLocalProvider(LocalContentBounds provides remember { mutableStateOf(IntRect.Zero) }) {
        AppShell(
            savedPanelWidth = savedPanelWidthDp?.dp,
            onPanelWidthSaved = { savedPanelWidthDp = it.value },
            drawerGesturesEnabled = backStacks.getValue(currentTab).size == 1,
            navPanel = { showLabels, onNavigated ->
                NavPanelColumn {
                    TopLevelTab.entries.forEach { tab ->
                        NavRow(tab.icon, stringResource(tab.label), selected = tab == currentTab, showLabel = showLabels) {
                            selectTab(tab)
                            onNavigated()
                        }
                    }
                }
            },
        ) { onMenuClick ->
            CompositionLocalProvider(LocalMenuClick provides onMenuClick) {
                NavDisplay(
                    entries = entries,
                    onBack = back,
                    modifier = modifier,
                    transitionSpec = { pushTransition() },
                    popTransitionSpec = { popTransition() },
                    predictivePopTransitionSpec = { popTransition() },
                )
            }
        }
    }
}
