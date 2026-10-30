package com.vipuljha.dupedigger.presentation.quarantine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppDialog
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.ui.StateSwitch
import com.vipuljha.dupedigger.core.ui.asString
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun QuarantineScreen(
    state: QuarantineState,
    onIntent: (QuarantineIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onMenuClick = onMenuClick) { TopBarTitle(stringResource(R.string.tab_quarantine)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        ) { items -> Items(items, state.busy, onIntent) }
    }
    state.confirmDelete?.let { item ->
        AppDialog(
            onDismissRequest = { onIntent(QuarantineIntent.DeleteDismissed) },
            onEnter = { onIntent(QuarantineIntent.DeleteConfirmed) },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text(stringResource(R.string.quarantine_delete_title, item.name)) },
            text = { Text(stringResource(R.string.quarantine_delete_body)) },
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
private fun Items(items: ImmutableList<QuarantineItemUi>, busy: ImmutableSet<Long>, onIntent: (QuarantineIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.maxWidthDetailHeader).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Dimens.listEndSpace),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            item(key = "note", contentType = "note") {
                Text(
                    stringResource(R.string.quarantine_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.spaceSm).animateItem(),
                )
            }
            items(items, key = { it.id }, contentType = { "item" }) { item ->
                ItemCard(item, item.id in busy, onIntent, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun ItemCard(item: QuarantineItemUi, busy: Boolean, onIntent: (QuarantineIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
            Column(horizontalAlignment = Alignment.End) {
                AppButton(
                    stringResource(R.string.quarantine_action_restore),
                    { onIntent(QuarantineIntent.RestoreClicked(item.id)) },
                    kind = ButtonKind.Tonal,
                    loading = busy,
                )
                AppButton(
                    stringResource(R.string.quarantine_action_delete),
                    { onIntent(QuarantineIntent.DeleteClicked(item)) },
                    kind = ButtonKind.Text,
                    enabled = !busy,
                )
            }
        }
    }
}
