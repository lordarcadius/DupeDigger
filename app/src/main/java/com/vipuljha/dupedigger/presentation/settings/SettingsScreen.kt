package com.vipuljha.dupedigger.presentation.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.vipuljha.dupedigger.domain.model.AppSettings
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onMenuClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenAi: () -> Unit = {},
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
                item(key = "similar", contentType = "similar") { SimilarFilesSection(state.nearThreshold, onIntent, Modifier.animateItem()) }
                item(key = "ai", contentType = "ai") { AiEntry(onOpenAi, Modifier.animateItem()) }
            }
        }
    }
}

@Composable
private fun AiEntry(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionHeader(stringResource(R.string.ai_title))
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.rowComfortableMin).clickable(onClick = onOpen).padding(vertical = Dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_ai_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** The slider follows the drag locally and saves once it is released, so dragging doesn't write the store on every step. */
@Composable
private fun SimilarFilesSection(threshold: Int, onIntent: (SettingsIntent) -> Unit, modifier: Modifier = Modifier) {
    val choices = AppSettings.NearThresholdChoices
    var value by remember(threshold) { mutableFloatStateOf(threshold.toFloat()) }
    Column(modifier) {
        SectionHeader(stringResource(R.string.settings_similar_title), description = stringResource(R.string.settings_similar_body))
        val stateText = stringResource(R.string.settings_similar_value, value.roundToInt())
        Text(stateText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Dimens.spaceSm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            Text(stringResource(R.string.settings_similar_strict), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onIntent(SettingsIntent.NearThresholdChanged(value.roundToInt())) },
                valueRange = choices.first.toFloat()..choices.last.toFloat(),
                steps = choices.last - choices.first - 1,
                modifier = Modifier.weight(1f).semantics { stateDescription = stateText },
            )
            Text(stringResource(R.string.settings_similar_loose), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
