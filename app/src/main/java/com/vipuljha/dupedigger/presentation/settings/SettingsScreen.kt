package com.vipuljha.dupedigger.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.SectionHeader
import com.vipuljha.dupedigger.core.designsystem.TooltipIconButton
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onMenuClick = onMenuClick) { TopBarTitle(stringResource(R.string.tab_settings)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
            return@Scaffold
        }
        val gutter = LocalPaneMetrics.current.gutter
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Dimens.maxWidthReading).fillMaxWidth(),
                contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Dimens.listEndSpace),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
            ) {
                folderSection(
                    purpose = FolderPurpose.Scan,
                    title = R.string.settings_folders_title,
                    description = R.string.settings_folders_body,
                    empty = R.string.settings_folders_empty,
                    addLabel = R.string.settings_folders_add,
                    icon = Icons.Outlined.Folder,
                    rows = state.folders,
                    onIntent = onIntent,
                )
                folderSection(
                    purpose = FolderPurpose.Exclude,
                    title = R.string.settings_excluded_title,
                    description = R.string.settings_excluded_body,
                    empty = R.string.settings_excluded_empty,
                    addLabel = R.string.settings_excluded_add,
                    icon = Icons.Outlined.FolderOff,
                    rows = state.excluded,
                    onIntent = onIntent,
                )
            }
        }
    }
}

private fun LazyListScope.folderSection(
    purpose: FolderPurpose,
    title: Int,
    description: Int,
    empty: Int,
    addLabel: Int,
    icon: ImageVector,
    rows: ImmutableList<FolderUiModel>,
    onIntent: (SettingsIntent) -> Unit,
) {
    item(key = "header-$purpose", contentType = "header") {
        SectionHeader(stringResource(title), description = stringResource(description), modifier = Modifier.animateItem())
    }
    if (rows.isEmpty()) {
        item(key = "empty-$purpose", contentType = "empty") {
            Text(
                stringResource(empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem(),
            )
        }
    }
    items(rows, key = { "$purpose-${it.key}" }, contentType = { "folder" }) { row ->
        FolderRow(row, icon, onRemove = { onIntent(SettingsIntent.RemoveClicked(purpose, row.key)) }, modifier = Modifier.animateItem())
    }
    item(key = "add-$purpose", contentType = "add") {
        AppButton(
            label = stringResource(addLabel),
            onClick = { onIntent(SettingsIntent.AddFolderClicked(purpose)) },
            kind = ButtonKind.Tonal,
            icon = Icons.Filled.Add,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun FolderRow(row: FolderUiModel, icon: ImageVector, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            modifier = Modifier.heightIn(min = Dimens.rowComfortableMin).padding(start = Dimens.spaceLg, end = Dimens.spaceXs),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Dimens.icon))
            Column(Modifier.weight(1f).padding(vertical = Dimens.spaceSm)) {
                Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TooltipIconButton(Icons.Filled.Close, stringResource(R.string.settings_remove), onRemove)
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    DupeDiggerTheme {
        SettingsScreen(
            state = SettingsState(
                folders = persistentListOf(FolderUiModel("a", "Camera", "/storage/emulated/0/DCIM/Camera")),
                excluded = persistentListOf(),
                isLoading = false,
            ),
            onIntent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onMenuClick = null,
        )
    }
}
