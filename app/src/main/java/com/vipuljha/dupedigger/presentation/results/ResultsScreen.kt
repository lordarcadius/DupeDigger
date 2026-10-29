package com.vipuljha.dupedigger.presentation.results

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppMotion
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.SkeletonBarFractions
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.designsystem.rememberShimmer
import com.vipuljha.dupedigger.core.designsystem.shimmer
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.domain.model.MediaKind

@Composable
fun ResultsScreen(
    state: ResultsState,
    groups: LazyPagingItems<GroupRowUi>,
    onIntent: (ResultsIntent) -> Unit,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onMenuClick = onMenuClick) { TopBarTitle(stringResource(R.string.tab_results)) } },
        bottomBar = { state.selectionSummary?.let { SelectionBar(it.asString()) { onIntent(ResultsIntent.ClearSelection) } } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.kindChips.size > 1) KindChips(state, onIntent)
            GroupList(state, groups, onIntent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KindChips(state: ResultsState, onIntent: (ResultsIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    LazyRow(
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        items(state.kindChips, key = { it.kind?.name ?: "all" }, contentType = { "chip" }) { chip ->
            FilterChip(
                selected = chip.kind == state.kind,
                onClick = { onIntent(ResultsIntent.KindSelected(chip.kind)) },
                label = { Text(chip.label.asString()) },
            )
        }
    }
}

/** Phases in the paged order: items on screen win, then a refresh error, then the first load, then empty. */
@Composable
private fun GroupList(state: ResultsState, groups: LazyPagingItems<GroupRowUi>, onIntent: (ResultsIntent) -> Unit, modifier: Modifier) {
    val refresh = groups.loadState.refresh
    var lastError by remember(state.kind) { mutableStateOf<LoadState.Error?>(null) }
    LaunchedEffect(refresh) {
        if (refresh is LoadState.Error) lastError = refresh
        if (refresh is LoadState.NotLoading) lastError = null
    }
    val error = refresh as? LoadState.Error ?: lastError?.takeIf { refresh is LoadState.Loading }
    val phase = when {
        groups.itemCount > 0 -> Phase.Content
        error != null -> Phase.Error
        refresh is LoadState.Loading -> Phase.Loading
        else -> Phase.Empty
    }
    Crossfade(phase, modifier, animationSpec = tween(AppMotion.StateFadeMillis, easing = FastOutSlowInEasing), label = "results") { p ->
        when (p) {
            Phase.Loading -> ResultsSkeleton()
            Phase.Error -> StateMessage(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.results_error_title),
                subtitle = stringResource(R.string.error_unknown),
                isError = true,
                action = { AppButton(stringResource(R.string.action_retry), groups::retry, loading = refresh is LoadState.Loading) },
            )
            Phase.Empty -> if (state.kind == null) {
                StateMessage(
                    icon = Icons.Outlined.FindInPage,
                    title = stringResource(R.string.results_empty_title),
                    subtitle = stringResource(R.string.results_empty_body),
                    action = { AppButton(stringResource(R.string.home_action_start_scan), { onIntent(ResultsIntent.StartScanClicked) }) },
                )
            } else {
                StateMessage(
                    icon = Icons.Outlined.FindInPage,
                    title = stringResource(R.string.results_empty_kind_title),
                    subtitle = stringResource(R.string.results_empty_kind_body),
                    action = { AppButton(stringResource(R.string.results_action_show_all), { onIntent(ResultsIntent.KindSelected(null)) }) },
                )
            }
            Phase.Content -> GroupRows(state, groups, onIntent)
        }
    }
}

private enum class Phase { Loading, Error, Empty, Content }

@Composable
private fun GroupRows(state: ResultsState, groups: LazyPagingItems<GroupRowUi>, onIntent: (ResultsIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    LazyColumn(contentPadding = PaddingValues(bottom = Dimens.listEndSpace)) {
        item(key = "actions", contentType = "actions") {
            Row(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Dimens.spaceSm), horizontalArrangement = Arrangement.End) {
                AppButton(
                    label = stringResource(R.string.results_action_select_duplicates),
                    onClick = { onIntent(ResultsIntent.SelectAllButKeepers) },
                    kind = ButtonKind.Tonal,
                    loading = state.selecting,
                )
            }
        }
        items(count = groups.itemCount, key = groups.itemKey { it.groupId }, contentType = groups.itemContentType { "group" }) { index ->
            groups[index]?.let { row -> GroupRow(row, { onIntent(ResultsIntent.GroupToggled(row)) }, Modifier.animateItem()) }
        }
        when (groups.loadState.append) {
            is LoadState.Loading -> item(key = "append", contentType = "append") {
                Box(Modifier.fillMaxWidth().padding(Dimens.spaceLg).animateItem(), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
            }
            is LoadState.Error -> item(key = "appendError", contentType = "appendError") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Dimens.spaceSm).animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.results_error_more), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppButton(stringResource(R.string.action_retry), groups::retry, kind = ButtonKind.Text)
                }
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun GroupRow(row: GroupRowUi, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val gutter = LocalPaneMetrics.current.gutter
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GroupRowMinHeight)
            .toggleable(value = row.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = gutter, vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(row.kind)
        Spacer(Modifier.width(Dimens.spaceLg))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.detail.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Dimens.spaceMd))
        Text(row.reclaim.asString(), style = MaterialTheme.typography.titleSmall)
        Checkbox(checked = row.selected, onCheckedChange = null, modifier = Modifier.padding(start = Dimens.spaceMd))
    }
}

@Composable
private fun KindBadge(kind: MediaKind) {
    Box(
        Modifier.size(KindBadgeSize).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(Dimens.icon))
    }
}

private fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.IMAGE -> Icons.Outlined.Image
    MediaKind.VIDEO -> Icons.Outlined.Movie
    MediaKind.AUDIO -> Icons.Outlined.Audiotrack
    MediaKind.DOCUMENT -> Icons.Outlined.Description
    MediaKind.OTHER -> Icons.Outlined.InsertDriveFile
}

@Composable
private fun SelectionBar(summary: String, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LocalPaneMetrics.current.gutter, vertical = Dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                summary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            AppButton(stringResource(R.string.results_action_clear), onClear, kind = ButtonKind.Text)
        }
    }
}

/** Rows in the real row's geometry, filling the viewport. */
@Composable
private fun ResultsSkeleton() {
    val shimmer = rememberShimmer()
    val gutter = LocalPaneMetrics.current.gutter
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        val rows = (maxHeight / GroupRowMinHeight).toInt() + 1
        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) {
            repeat(rows) { row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = GroupRowMinHeight).padding(horizontal = gutter, vertical = Dimens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(KindBadgeSize).shimmer(shimmer, MaterialTheme.shapes.medium))
                    Spacer(Modifier.width(Dimens.spaceLg))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier.fillMaxWidth(SkeletonBarFractions[row % SkeletonBarFractions.size])
                                .height(with(density) { typography.bodyLarge.lineHeight.toDp() })
                                .shimmer(shimmer, MaterialTheme.shapes.extraSmall),
                        )
                        Box(
                            Modifier.fillMaxWidth(SkeletonBarFractions[(row + 3) % SkeletonBarFractions.size])
                                .height(with(density) { typography.bodyMedium.lineHeight.toDp() })
                                .shimmer(shimmer, MaterialTheme.shapes.extraSmall),
                        )
                    }
                }
            }
        }
    }
}

private val GroupRowMinHeight = 72.dp
private val KindBadgeSize = 40.dp
