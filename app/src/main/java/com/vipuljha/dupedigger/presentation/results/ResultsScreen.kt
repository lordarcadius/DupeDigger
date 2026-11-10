package com.vipuljha.dupedigger.presentation.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppSnackbarHost
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppDialog
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppProgressBar
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.listEndPadding
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.SkeletonBarFractions
import com.vipuljha.dupedigger.core.designsystem.StateAlpha
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TierTag
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.designsystem.listPlacementSpec
import com.vipuljha.dupedigger.core.designsystem.rememberShimmer
import com.vipuljha.dupedigger.core.designsystem.stateChangeSpec
import com.vipuljha.dupedigger.core.designsystem.shimmer
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.domain.model.KeepRule
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.ImmutableList

@Composable
fun ResultsScreen(
    state: ResultsState,
    groups: LazyPagingItems<GroupRowUi>,
    onIntent: (ResultsIntent) -> Unit,
    openGroup: String?,
    onOpenGroup: (fingerprint: String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar() { TopBarTitle(stringResource(R.string.tab_results)) } },
        bottomBar = { SelectionBar(state, onIntent) },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.showKindChips) KindChips(state, onIntent)
            if (state.showTierChips) TierChips(state, onIntent)
            GroupList(state, groups, onIntent, openGroup, onOpenGroup, Modifier.weight(1f))
        }
    }
    state.confirmDelete?.let { DeleteDialog(it, onIntent) }
    state.folderChoices?.let { FolderDialog(it, onIntent) }
}

/** Picking a folder selects every other copy in the matching groups at once. */
@Composable
private fun FolderDialog(folders: ImmutableList<FolderChoiceUi>, onIntent: (ResultsIntent) -> Unit) {
    AppDialog(
        onDismissRequest = { onIntent(ResultsIntent.FolderPickerDismissed) },
        onEnter = { onIntent(ResultsIntent.FolderPickerDismissed) },
        title = { Text(stringResource(R.string.results_keep_folder_title)) },
        text = {
            Column(Modifier.heightIn(max = FolderListMaxHeight).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.results_keep_folder_body), modifier = Modifier.padding(bottom = Dimens.spaceSm))
                folders.forEach { folder ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.touchTarget)
                            .clickable(role = Role.Button) { onIntent(ResultsIntent.KeepRuleSelected(KeepRule.InFolder(folder.path))) }
                            .padding(vertical = Dimens.spaceSm),
                    ) {
                        Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                        Text(folder.detail.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { AppButton(stringResource(R.string.action_cancel), { onIntent(ResultsIntent.FolderPickerDismissed) }, kind = ButtonKind.Text) },
    )
}

private val FolderListMaxHeight = 320.dp

@Composable
private fun DeleteDialog(confirm: DeleteConfirmUi, onIntent: (ResultsIntent) -> Unit) {
    AppDialog(
        onDismissRequest = { onIntent(ResultsIntent.DeleteDismissed) },
        onEnter = { onIntent(ResultsIntent.DeleteConfirmed) },
        title = { Text(confirm.title.asString()) },
        text = {
            Column(
                Modifier.heightIn(max = DialogTextMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                Text(confirm.body.asString())
                confirm.freeAfter?.let {
                    Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                confirm.similarWarning?.let {
                    Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        confirmButton = { AppButton(stringResource(R.string.action_delete), { onIntent(ResultsIntent.DeleteConfirmed) }, kind = ButtonKind.Destructive) },
        dismissButton = { AppButton(stringResource(R.string.action_cancel), { onIntent(ResultsIntent.DeleteDismissed) }, kind = ButtonKind.Text) },
    )
}

private val DialogTextMaxHeight = 320.dp

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
                label = { Text(chip.label.asString(), maxLines = 1) },
                modifier = Modifier.animateItem(placementSpec = listPlacementSpec()),
            )
        }
    }
}

/** One tap selects every copy but the one each rule keeps; the chip of the rule behind the selection stays on. */
@Composable
private fun KeepChips(state: ResultsState, onIntent: (ResultsIntent) -> Unit, modifier: Modifier = Modifier) {
    val gutter = LocalPaneMetrics.current.gutter
    val chips = listOf(
        KeepRule.Newest to R.string.results_keep_newest,
        KeepRule.Oldest to R.string.results_keep_oldest,
        KeepRule.Largest to R.string.results_keep_largest,
    )
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        items(chips, key = { it.second }, contentType = { "keep" }) { (rule, label) ->
            FilterChip(
                selected = state.keepRule == rule,
                onClick = { onIntent(ResultsIntent.KeepRuleSelected(rule)) },
                enabled = !state.selecting,
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
        item(key = "keep-folder", contentType = "keep") {
            FilterChip(
                selected = state.keepRule is KeepRule.InFolder,
                onClick = { onIntent(ResultsIntent.KeepFromFolderClicked) },
                enabled = !state.selecting,
                label = { Text(stringResource(R.string.results_keep_folder), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun TierChips(state: ResultsState, onIntent: (ResultsIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    LazyRow(
        modifier = Modifier.padding(top = Dimens.spaceSm),
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        items(state.tierChips, key = { it.tier?.name ?: "all" }, contentType = { "chip" }) { chip ->
            FilterChip(
                selected = chip.tier == state.tier,
                onClick = { onIntent(ResultsIntent.TierSelected(chip.tier)) },
                label = { Text(chip.label.asString(), maxLines = 1) },
                modifier = Modifier.animateItem(placementSpec = listPlacementSpec()),
            )
        }
    }
}

/** Phases in the paged order: items on screen win, then a refresh error, then the first load, then empty. */
@Composable
private fun GroupList(
    state: ResultsState,
    groups: LazyPagingItems<GroupRowUi>,
    onIntent: (ResultsIntent) -> Unit,
    openGroup: String?,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier,
) {
    val refresh = groups.loadState.refresh
    var lastError by remember(state.kind, state.tier) { mutableStateOf<LoadState.Error?>(null) }
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
    Crossfade(phase, modifier, animationSpec = stateChangeSpec(), label = "results") { p ->
        when (p) {
            Phase.Loading -> ResultsSkeleton()
            Phase.Error -> StateMessage(
                icon = Icons.Outlined.ErrorOutline,
                title = stringResource(R.string.results_error_title),
                subtitle = stringResource(R.string.error_unknown),
                isError = true,
                action = { AppButton(stringResource(R.string.action_retry), groups::retry, loading = refresh is LoadState.Loading) },
            )
            Phase.Empty -> if (state.kind == null && state.tier == null) {
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
                    action = { AppButton(stringResource(R.string.results_action_show_all), { onIntent(ResultsIntent.ShowAllClicked) }) },
                )
            }
            Phase.Content -> GroupRows(state, groups, onIntent, openGroup, onOpenGroup)
        }
    }
}

private enum class Phase { Loading, Error, Empty, Content }

@Composable
private fun GroupRows(
    state: ResultsState,
    groups: LazyPagingItems<GroupRowUi>,
    onIntent: (ResultsIntent) -> Unit,
    openGroup: String?,
    onOpenGroup: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = listEndPadding())) {
        item(key = "select-all", contentType = "select-all") { SelectAllRow(state, onIntent) }
        item(key = "keep-rules", contentType = "keep-rules") {
            KeepChips(state, onIntent, Modifier.padding(bottom = Dimens.spaceSm))
        }
        items(count = groups.itemCount, key = groups.itemKey { it.groupId }, contentType = groups.itemContentType { "group" }) { index ->
            groups[index]?.let { row ->
                GroupRow(
                    row,
                    open = row.fingerprint == openGroup,
                    onOpen = { onOpenGroup(row.fingerprint) },
                    onToggle = { onIntent(ResultsIntent.GroupToggled(row)) },
                    modifier = Modifier.animateItem(placementSpec = listPlacementSpec()),
                )
            }
        }
        when (groups.loadState.append) {
            is LoadState.Loading -> item(key = "append", contentType = "append") {
                Box(Modifier.fillMaxWidth().padding(Dimens.spaceLg).animateItem(), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
            }
            is LoadState.Error -> item(key = "appendError", contentType = "appendError") {
                val gutter = LocalPaneMetrics.current.gutter
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Dimens.spaceSm).animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.results_error_more), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppButton(stringResource(R.string.action_retry), groups::retry, kind = ButtonKind.Text)
                }
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

/**
 * The one control that selects or clears every copy the filter shows.
 * Its box is half-checked while part of them are picked, and its line says what the whole lot would free.
 */
@Composable
private fun SelectAllRow(state: ResultsState, onIntent: (ResultsIntent) -> Unit, modifier: Modifier = Modifier) {
    val gutter = LocalPaneMetrics.current.gutter
    val label = stringResource(R.string.results_action_select_all)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .toggleable(
                value = state.allSelected,
                enabled = !state.selecting && (state.selectable.files > 0 || state.hasSelection),
                role = Role.Checkbox,
                onValueChange = { onIntent(ResultsIntent.SelectAllToggled) },
            )
            .padding(horizontal = gutter, vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = when {
                state.allSelected -> ToggleableState.On
                state.hasSelection -> ToggleableState.Indeterminate
                else -> ToggleableState.Off
            },
            onClick = null,
            enabled = !state.selecting && (state.selectable.files > 0 || state.hasSelection),
        )
        Spacer(Modifier.width(Dimens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            state.selectable.detail?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.selecting) {
            Spacer(Modifier.width(Dimens.spaceSm))
            AppLoadingIndicator(Modifier.size(Dimens.iconDense))
        }
    }
}

@Composable
private fun GroupRow(row: GroupRowUi, open: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = LocalPaneMetrics.current
    val gutter = metrics.gutter
    val narrow = metrics.width < NarrowRowWidth * LocalDensity.current.fontScale
    val selectLabel = stringResource(R.string.results_select_group, row.title)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GroupRowMinHeight)
            .then(if (open) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = StateAlpha.Selected)) else Modifier)
            .semantics { selected = open }
            .clickable(onClickLabel = stringResource(R.string.results_open_group), onClick = onOpen)
            .padding(horizontal = gutter, vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupThumbnail(row)
        Spacer(Modifier.width(Dimens.spaceMd))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                row.tierTag?.let { TierTag(it.asString()) }
            }
            Text(
                row.detail.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (narrow) Text(row.reclaim.asString(), style = MaterialTheme.typography.titleSmall)
        }
        if (!narrow) {
            Spacer(Modifier.width(Dimens.spaceSm))
            Text(row.reclaim.asString(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        TriStateCheckbox(
            state = when {
                row.selected -> ToggleableState.On
                row.partlySelected -> ToggleableState.Indeterminate
                else -> ToggleableState.Off
            },
            onClick = onToggle,
            modifier = Modifier.semantics { contentDescription = selectLabel },
        )
    }
}

/** The keeper's own thumbnail for photos and video, the kind's glyph for everything else. */
@Composable
private fun GroupThumbnail(row: GroupRowUi) {
    val shape = MaterialTheme.shapes.medium
    if (row.thumbnail != null) {
        AsyncImage(
            model = row.thumbnail,
            contentDescription = null,
            placeholder = painterResource(R.drawable.art_thumb_placeholder),
            error = painterResource(R.drawable.art_thumb_broken),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(KindBadgeSize).clip(shape),
        )
    } else {
        Box(
            Modifier.size(KindBadgeSize).background(MaterialTheme.colorScheme.secondaryContainer, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(row.kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(Dimens.icon))
        }
    }
}

private fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.IMAGE -> Icons.Outlined.Image
    MediaKind.VIDEO -> Icons.Outlined.Movie
    MediaKind.AUDIO -> Icons.Outlined.Audiotrack
    MediaKind.DOCUMENT -> Icons.Outlined.Description
    MediaKind.OTHER -> Icons.Outlined.InsertDriveFile
}

/**
 * The bar that appears with the first selection and carries the delete.
 * Its buttons wrap under the summary when the pane is too narrow to hold them on one line, so nothing is cut off.
 */
@Composable
private fun SelectionBar(state: ResultsState, onIntent: (ResultsIntent) -> Unit) {
    AnimatedVisibility(
        visible = state.selectionSummary != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        // Sits clear of the floating navigation bar, which overlays the bottom of the pane.
        val summary = state.selectionSummary?.asString().orEmpty()
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(bottom = LocalPaneMetrics.current.bottomOverlay),
        ) {
            Column {
                if (state.deleting) {
                    AppProgressBar(
                        progress = { state.deleteProgress }.takeIf { state.deleteProgress > 0f },
                        modifier = Modifier.padding(horizontal = LocalPaneMetrics.current.gutter, vertical = Dimens.spaceXs),
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = LocalPaneMetrics.current.gutter, vertical = Dimens.spaceSm),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
                ) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .align(Alignment.CenterVertically)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    AppButton(
                        stringResource(R.string.results_action_clear),
                        { onIntent(ResultsIntent.ClearSelection) },
                        kind = ButtonKind.Text,
                        enabled = !state.deleting,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    AppButton(
                        stringResource(R.string.action_delete),
                        { onIntent(ResultsIntent.DeleteClicked) },
                        icon = Icons.Outlined.Delete,
                        loading = state.deleting,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
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
                    Spacer(Modifier.width(Dimens.spaceMd))
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

// Below this (scaled with the font size) the size moves under the name, so the name keeps its room.
private val NarrowRowWidth = 400.dp
private val KindBadgeSize = 48.dp
