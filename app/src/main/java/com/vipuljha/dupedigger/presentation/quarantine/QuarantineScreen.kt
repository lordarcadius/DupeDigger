package com.vipuljha.dupedigger.presentation.quarantine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppDialog
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppSnackbarHost
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.designsystem.listEndPadding
import com.vipuljha.dupedigger.core.ui.StateSwitch
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.core.ui.formatCount
import kotlinx.collections.immutable.ImmutableList

@Composable
fun QuarantineScreen(
    state: QuarantineState,
    onIntent: (QuarantineIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar { TopBarTitle(stringResource(R.string.tab_quarantine)) } },
        bottomBar = { ActionBar(state, onIntent) },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        StateSwitch(
            state = state.content,
            isEmpty = { it.isEmpty() },
            modifier = Modifier.fillMaxSize().padding(padding),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() } },
            empty = {
                StateMessage(Icons.Outlined.Inventory2, stringResource(R.string.quarantine_empty_title), stringResource(R.string.quarantine_empty_body))
            },
            error = { error ->
                StateMessage(Icons.Outlined.ErrorOutline, stringResource(R.string.quarantine_error_title), error.message.asString(), isError = true)
            },
        ) { items -> Items(items, state, onIntent) }
    }
    state.confirmDelete?.let { count ->
        AppDialog(
            onDismissRequest = { onIntent(QuarantineIntent.DeleteDismissed) },
            onEnter = { onIntent(QuarantineIntent.DeleteConfirmed) },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text(pluralStringResource(R.plurals.quarantine_delete_all_title, count, formatCount(count))) },
            text = { Text(stringResource(R.string.quarantine_delete_all_body)) },
            confirmButton = {
                AppButton(stringResource(R.string.action_delete), { onIntent(QuarantineIntent.DeleteConfirmed) }, kind = ButtonKind.Destructive)
            },
            dismissButton = {
                AppButton(stringResource(R.string.action_cancel), { onIntent(QuarantineIntent.DeleteDismissed) }, kind = ButtonKind.Text)
            },
        )
    }
}

@Composable
private fun Items(items: ImmutableList<QuarantineItemUi>, state: QuarantineState, onIntent: (QuarantineIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.maxWidthDetailHeader).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = listEndPadding()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            item(key = "note", contentType = "note") {
                Text(
                    stringResource(R.string.quarantine_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "select-all", contentType = "select-all") { SelectAllRow(items.size, state, onIntent) }
            items(items, key = { it.id }, contentType = { "item" }) { item ->
                ItemCard(item, state.working, onIntent, Modifier.animateItem())
            }
        }
    }
}

/** One control that picks or clears every file here, half-checked while only some are picked. */
@Composable
private fun SelectAllRow(total: Int, state: QuarantineState, onIntent: (QuarantineIntent) -> Unit) {
    val all = state.selection.size >= total && total > 0
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .toggleable(
                value = all,
                enabled = !state.working,
                role = Role.Checkbox,
                onValueChange = { onIntent(QuarantineIntent.SelectAllToggled) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = when {
                all -> ToggleableState.On
                state.hasSelection -> ToggleableState.Indeterminate
                else -> ToggleableState.Off
            },
            onClick = null,
            enabled = !state.working,
        )
        Spacer(Modifier.width(Dimens.spaceMd))
        Text(stringResource(R.string.quarantine_select_all), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ItemCard(item: QuarantineItemUi, working: Boolean, onIntent: (QuarantineIntent) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.quarantine_select_file, item.name)
    Surface(
        modifier
            .fillMaxWidth()
            .toggleable(
                value = item.selected,
                enabled = !working,
                role = Role.Checkbox,
                onValueChange = { onIntent(QuarantineIntent.ItemToggled(item.id)) },
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(Dimens.spaceLg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXxs)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.folder.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(item.details.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Dimens.spaceSm))
            Checkbox(
                checked = item.selected,
                onCheckedChange = null,
                enabled = !working,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

/** Appears with the first pick and carries both actions, clear of the floating navigation bar. */
@Composable
private fun ActionBar(state: QuarantineState, onIntent: (QuarantineIntent) -> Unit) {
    AnimatedVisibility(
        visible = state.hasSelection,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(bottom = LocalPaneMetrics.current.bottomOverlay),
        ) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = LocalPaneMetrics.current.gutter, vertical = Dimens.spaceSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                Text(
                    pluralStringResource(R.plurals.quarantine_selected, state.selection.size, formatCount(state.selection.size)),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .align(Alignment.CenterVertically)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                AppButton(
                    stringResource(R.string.action_delete),
                    { onIntent(QuarantineIntent.DeleteClicked) },
                    kind = ButtonKind.Text,
                    enabled = !state.working,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                AppButton(
                    stringResource(R.string.quarantine_action_restore),
                    { onIntent(QuarantineIntent.RestoreClicked) },
                    loading = state.working,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}
