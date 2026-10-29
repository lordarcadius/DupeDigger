package com.vipuljha.dupedigger.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.SectionHeader
import com.vipuljha.dupedigger.core.designsystem.Shimmer
import com.vipuljha.dupedigger.core.designsystem.SkeletonBarFractions
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.designsystem.rememberShimmer
import com.vipuljha.dupedigger.core.designsystem.shimmer
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.StateSwitch
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.persistentListOf

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onMenuClick = onMenuClick) { TopBarTitle(stringResource(R.string.tab_home)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        StateSwitch(
            state = state.content,
            isEmpty = { it.isEmpty },
            modifier = Modifier.fillMaxSize().padding(padding),
            loading = { HomeSkeleton() },
            empty = {
                StateMessage(
                    icon = Icons.Outlined.FindInPage,
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_body),
                    action = { AppButton(stringResource(R.string.home_action_start_scan), { onIntent(HomeIntent.OpenScanClicked) }) },
                )
            },
            error = { error ->
                StateMessage(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.home_error_title),
                    subtitle = error.message.asString(),
                    isError = true,
                    action = { AppButton(stringResource(R.string.action_retry), { onIntent(HomeIntent.Retry) }, loading = error.retrying) },
                )
            },
        ) { model -> HomeContent(model, state, onIntent) }
    }
}

@Composable
private fun HomeContent(model: HomeUiModel, state: HomeState, onIntent: (HomeIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.maxWidthReading).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Dimens.listEndSpace),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            item(key = "summary", contentType = "summary") { SummaryCard(model, Modifier.animateItem()) }
            item(key = "last-scan", contentType = "last-scan") {
                LastScanCard(model, onViewProgress = { onIntent(HomeIntent.OpenScanClicked) }, modifier = Modifier.animateItem())
            }
            item(key = "actions-header", contentType = "header") {
                SectionHeader(stringResource(R.string.home_actions_title), modifier = Modifier.animateItem())
            }
            items(state.quickActions, key = { it.action }, contentType = { "action" }) { action ->
                QuickActionRow(
                    action = action,
                    loading = state.startingAction == action.action,
                    busy = state.startingAction != null || model.scanning,
                    onClick = { onIntent(HomeIntent.QuickActionClicked(action.action)) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "custom", contentType = "custom") {
                AppButton(
                    label = stringResource(R.string.home_action_custom_scan),
                    onClick = { onIntent(HomeIntent.OpenScanClicked) },
                    icon = Icons.Filled.Tune,
                    modifier = Modifier.padding(top = Dimens.spaceSm).animateItem(),
                )
            }
        }
    }
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(Dimens.spaceLg), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) { content() }
    }
}

@Composable
private fun SummaryCard(model: HomeUiModel, modifier: Modifier = Modifier) {
    HomeCard(modifier) {
        Text(stringResource(R.string.home_reclaim_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(model.reclaimTotal.asString(), style = MaterialTheme.typography.displaySmallEmphasized)
        Text(
            (model.reclaimDetail ?: UiText.res(R.string.home_reclaim_none)).asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        model.kinds.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.label.asString(), style = MaterialTheme.typography.bodyLarge)
                    Text(row.detail.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(row.size.asString(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun LastScanCard(model: HomeUiModel, onViewProgress: () -> Unit, modifier: Modifier = Modifier) {
    HomeCard(modifier) {
        Text(stringResource(R.string.home_last_scan_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.scanning) {
            Text(stringResource(R.string.home_scan_status_running), style = MaterialTheme.typography.titleMedium)
            AppButton(stringResource(R.string.home_action_view_progress), onViewProgress, kind = ButtonKind.Text)
            return@HomeCard
        }
        val last = model.lastScan ?: return@HomeCard
        Text(last.status.asString(), style = MaterialTheme.typography.titleMedium)
        Text(
            last.scope?.let { UiText.res(R.string.home_last_scan_detail, last.time, it) }?.asString() ?: last.time.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickActionRow(action: QuickActionUi, loading: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (label, icon) = when (action.action) {
        QuickAction.Everything -> R.string.home_action_scan_everything to Icons.Filled.PhoneAndroid
        QuickAction.Media -> R.string.home_action_scan_media to Icons.Filled.PhotoLibrary
        QuickAction.Folder -> R.string.home_action_scan_folder to Icons.Filled.Folder
    }
    Column(modifier.fillMaxWidth()) {
        AppButton(
            label = stringResource(label),
            onClick = onClick,
            kind = ButtonKind.Tonal,
            icon = icon,
            loading = loading,
            enabled = action.enabled && !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        action.hint?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spaceLg, vertical = Dimens.spaceXs),
            )
        }
    }
}

/** The summary and last-scan cards' geometry, shimmering, while the first values load. */
@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmer()
    val gutter = LocalPaneMetrics.current.gutter
    val typography = MaterialTheme.typography
    Box(modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = Dimens.maxWidthReading).fillMaxWidth().padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            SkeletonCard(shimmer, listOf(typography.titleMedium, typography.displaySmallEmphasized, typography.bodyMedium), 0)
            SkeletonCard(shimmer, listOf(typography.titleMedium, typography.titleMedium, typography.bodyMedium), 3)
        }
    }
}

@Composable
private fun SkeletonCard(shimmer: Shimmer, lines: List<TextStyle>, offset: Int) {
    HomeCard {
        lines.forEachIndexed { i, style ->
            val height = with(LocalDensity.current) { style.lineHeight.toDp() }
            Box(
                Modifier
                    .fillMaxWidth(SkeletonBarFractions[(i + offset) % SkeletonBarFractions.size])
                    .height(height)
                    .shimmer(shimmer, MaterialTheme.shapes.extraSmall),
            )
        }
        Spacer(Modifier.height(Dimens.spaceXs))
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    DupeDiggerTheme(dynamicColor = false) {
        HomeScreen(
            state = HomeState(
                content = ContentState.Success(
                    HomeUiModel(
                        reclaimTotal = UiText.Raw("1.2 GB"),
                        reclaimDetail = UiText.Raw("312 duplicate files · 40 groups"),
                        kinds = persistentListOf(KindRowUi(MediaKind.IMAGE, UiText.Raw("Images"), UiText.Raw("900 MB"), UiText.Raw("280 duplicate files"))),
                        lastScan = LastScanUi(UiText.Raw("5 minutes ago"), UiText.Raw("Photos, videos and audio"), UiText.Raw("Finished")),
                        scanning = false,
                    ),
                ),
                quickActions = quickActions(emptyList()),
            ),
            onIntent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onMenuClick = {},
        )
    }
}
