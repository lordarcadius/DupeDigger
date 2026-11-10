package com.vipuljha.dupedigger.presentation.scan

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppDialog
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppProgressBar
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.SectionHeader
import com.vipuljha.dupedigger.core.designsystem.stateChangeSpec
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TooltipIconButton
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.domain.model.ScopeKind
import kotlinx.collections.immutable.persistentListOf

/** [progress] is read only where it is drawn, so live ticks don't recompose the screen. */
@Composable
fun ScanScreen(
    state: ScanState,
    progress: () -> ScanProgressUi?,
    onIntent: (ScanIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when {
        state.draft == null -> R.string.scan_title
        state.draft.id == 0L -> R.string.scan_action_new_preset
        else -> R.string.scan_action_edit_preset
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onBack = onBack) { TopBarTitle(stringResource(title)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Crossfade(
            targetState = state.phase,
            modifier = Modifier.fillMaxSize().padding(padding),
            animationSpec = stateChangeSpec(),
            label = "scanPhase",
        ) { phase ->
            when (phase) {
                ScanPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
                ScanPhase.Setup -> SetupPanel(state, onIntent)
                ScanPhase.Running -> RunningPanel(progress, state.cancelling, state.aiSkipped, onIntent)
                is ScanPhase.Done -> DonePanel(phase.result, onIntent)
            }
        }
    }
    state.confirmDelete?.let { preset ->
        AppDialog(
            onDismissRequest = { onIntent(ScanIntent.DeleteDismissed) },
            onEnter = { onIntent(ScanIntent.DeleteConfirmed) },
            title = { Text(stringResource(R.string.scan_preset_delete_title, preset.title.asString())) },
            text = { Text(stringResource(R.string.scan_preset_delete_body)) },
            confirmButton = { AppButton(stringResource(R.string.action_delete), { onIntent(ScanIntent.DeleteConfirmed) }, kind = ButtonKind.Destructive) },
            dismissButton = { AppButton(stringResource(R.string.action_cancel), { onIntent(ScanIntent.DeleteDismissed) }, kind = ButtonKind.Text) },
        )
    }
}

/** One step at a time: presets, then file types, then where to look; each ends in its own action row. */
@Composable
private fun SetupPanel(state: ScanState, onIntent: (ScanIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.maxWidthReading).fillMaxWidth().testTag(ScanTestTags.setupList),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Dimens.listEndSpace),
        ) {
            when (state.step) {
                SetupStep.Presets -> presetsStep(state, onIntent)
                SetupStep.Types -> typesStep(state, onIntent)
                SetupStep.Where -> whereStep(state, onIntent)
            }
        }
    }
}

private fun LazyListScope.presetsStep(state: ScanState, onIntent: (ScanIntent) -> Unit) {
    item(key = "presets-header", contentType = "header") {
        SectionHeader(stringResource(R.string.scan_presets_title), Modifier.animateItem())
    }
    item(key = "preset-none", contentType = "preset") {
        ChoiceRow(
            title = stringResource(R.string.scan_preset_none),
            body = stringResource(R.string.scan_preset_none_body),
            // Says what Start would do right here, so the common case needs no trip through the other two steps.
            hint = state.defaultScopeTitle?.asString(),
            selected = state.selectedPresetId == null,
            modifier = Modifier.animateItem(),
        ) { onIntent(ScanIntent.PresetSelected(null)) }
    }
    items(state.presets, key = { "preset-${it.id}" }, contentType = { "preset" }) { preset ->
        PresetRow(preset, selected = preset.id == state.selectedPresetId, onIntent = onIntent, modifier = Modifier.animateItem())
    }
    tiers(state, onIntent)
    item(key = "presets-actions", contentType = "actions") {
        ActionRow(Modifier.animateItem()) {
            AppButton(stringResource(R.string.scan_action_new_preset), { onIntent(ScanIntent.NewPresetClicked) }, kind = ButtonKind.Text)
            if (state.selectedPresetId == null) {
                AppButton(stringResource(R.string.scan_action_customise), { onIntent(ScanIntent.NextClicked) }, kind = ButtonKind.Outlined)
            }
            AppButton(
                stringResource(R.string.scan_action_start),
                { onIntent(ScanIntent.StartClicked) },
                loading = state.starting,
                enabled = if (state.selectedPresetId == null) state.canFinish else state.canStartPreset,
            )
        }
    }
}

private fun LazyListScope.typesStep(state: ScanState, onIntent: (ScanIntent) -> Unit) {
    item(key = "types-header", contentType = "header") {
        SectionHeader(stringResource(R.string.scan_types_title), Modifier.animateItem())
    }
    items(state.kinds, key = { "kind-${it.kind}" }, contentType = { "check" }) { option ->
        CheckRow(option.label.asString(), option.selected, Modifier.animateItem()) { onIntent(ScanIntent.KindToggled(option.kind)) }
    }
    item(key = "types-actions", contentType = "actions") {
        ActionRow(Modifier.animateItem()) {
            AppButton(stringResource(R.string.scan_action_next), { onIntent(ScanIntent.NextClicked) }, enabled = state.canLeaveTypes)
        }
    }
}

private fun LazyListScope.whereStep(state: ScanState, onIntent: (ScanIntent) -> Unit) {
    state.draft?.let { draft ->
        item(key = "draft-name", contentType = "name") {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onIntent(ScanIntent.DraftNameChanged(it)) },
                label = { Text(stringResource(R.string.scan_preset_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.spaceSm).animateItem(),
            )
        }
    }
    item(key = "where-header", contentType = "header") {
        SectionHeader(stringResource(R.string.scan_where_title), Modifier.animateItem())
    }
    items(state.scopes, key = { "scope-${it.kind}" }, contentType = { "choice" }) { option ->
        ChoiceRow(
            title = option.title.asString(),
            body = option.body.asString(),
            selected = option.kind == state.selectedScope,
            modifier = Modifier.animateItem(),
        ) { onIntent(ScanIntent.ScopeSelected(option.kind)) }
    }
    if (state.selectedScope == ScopeKind.FOLDERS) {
        items(state.folders, key = { "folder-${it.uri}" }, contentType = { "check" }) { folder ->
            CheckRow(folder.name, folder.selected, Modifier.padding(start = Dimens.spaceXl).animateItem()) {
                onIntent(ScanIntent.FolderToggled(folder.uri))
            }
        }
        item(key = "add-folder", contentType = "add-folder") {
            AppButton(
                stringResource(R.string.scan_action_add_folder),
                { onIntent(ScanIntent.AddFolderClicked) },
                kind = ButtonKind.Text,
                icon = Icons.Filled.CreateNewFolder,
                modifier = Modifier.padding(start = Dimens.spaceLg).animateItem(),
            )
        }
    }
    if (state.draft == null) tiers(state, onIntent)
    item(key = "where-actions", contentType = "actions") {
        ActionRow(Modifier.animateItem()) {
            if (state.draft == null) {
                AppButton(stringResource(R.string.scan_action_start), { onIntent(ScanIntent.StartClicked) }, loading = state.starting, enabled = state.canFinish)
            } else {
                AppButton(stringResource(R.string.scan_action_save_preset), { onIntent(ScanIntent.SavePresetClicked) }, loading = state.saving, enabled = state.canFinish)
            }
        }
    }
}

private fun LazyListScope.tiers(state: ScanState, onIntent: (ScanIntent) -> Unit) {
    item(key = "tier-header", contentType = "header") { SectionHeader(stringResource(R.string.scan_tier_title), Modifier.animateItem()) }
    item(key = "tier-exact", contentType = "tier") {
        TierRow(stringResource(R.string.scan_tier_exact), stringResource(R.string.scan_tier_exact_body), checked = true, enabled = false, Modifier.animateItem()) {}
    }
    item(key = "tier-near", contentType = "tier") {
        TierRow(stringResource(R.string.scan_tier_near), stringResource(R.string.scan_tier_near_body), state.nearEnabled, enabled = true, Modifier.animateItem()) {
            onIntent(ScanIntent.NearToggled(it))
        }
    }
    item(key = "tier-ai", contentType = "tier") {
        TierRow(
            stringResource(R.string.scan_tier_ai),
            stringResource(if (state.aiAvailable) R.string.scan_tier_ai_on_body else R.string.scan_tier_ai_body),
            state.aiEnabled,
            enabled = state.aiAvailable,
            Modifier.animateItem(),
        ) { onIntent(ScanIntent.AiToggled(it)) }
    }
}

/** Buttons wrap to a second line instead of being cut off when the pane or the font leaves no room. */
@Composable
private fun ActionRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = Dimens.spaceXl),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        content = content,
    )
}

@Composable
private fun PresetRow(preset: PresetUi, selected: Boolean, onIntent: (ScanIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ChoiceRow(
            title = preset.title.asString(),
            body = preset.body.asString(),
            hint = preset.hint?.asString(),
            selected = selected,
            modifier = Modifier.weight(1f),
        ) { onIntent(ScanIntent.PresetSelected(preset.id)) }
        if (preset.editable) {
            TooltipIconButton(Icons.Filled.Edit, stringResource(R.string.scan_action_edit_preset), { onIntent(ScanIntent.EditPresetClicked(preset.id)) })
            TooltipIconButton(Icons.Filled.Delete, stringResource(R.string.scan_action_delete_preset), { onIntent(ScanIntent.DeletePresetClicked(preset.id)) })
        }
    }
}

@Composable
private fun ChoiceRow(title: String, body: String, selected: Boolean, modifier: Modifier = Modifier, hint: String? = null, onSelect: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowComfortableMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Dimens.spaceLg))
        Column(Modifier.weight(1f)) {
            RowText(title, body)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(Dimens.spaceLg))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TierRow(title: String, body: String, checked: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowComfortableMin)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, body, Modifier.weight(1f))
        Spacer(Modifier.width(Dimens.spaceLg))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun RowText(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

object ScanTestTags {
    const val setupList = "scan-setup-list"
}

@Composable
private fun RunningPanel(progress: () -> ScanProgressUi?, cancelling: Boolean, aiSkipped: Boolean, onIntent: (ScanIntent) -> Unit) {
    val aiStage by remember(progress) { derivedStateOf { progress()?.aiStage == true } }
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize().padding(horizontal = gutter), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = Dimens.maxWidthReading).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProgressNumbers(progress)
            Spacer(Modifier.height(Dimens.spaceXl))
            StageProgressBar(progress)
            Spacer(Modifier.height(Dimens.spaceXl))
            Text(
                stringResource(R.string.scan_running_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dimens.spaceLg))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                if (aiStage) {
                    AppButton(
                        stringResource(R.string.scan_action_skip_ai),
                        { onIntent(ScanIntent.SkipAiClicked) },
                        kind = ButtonKind.Text,
                        loading = aiSkipped,
                        enabled = !aiSkipped,
                    )
                }
                AppButton(
                    stringResource(R.string.scan_action_cancel_scan),
                    { onIntent(ScanIntent.CancelClicked) },
                    kind = ButtonKind.Outlined,
                    loading = cancelling,
                )
            }
        }
    }
}

@Composable
private fun ProgressNumbers(progress: () -> ScanProgressUi?) {
    val current = progress()
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            (current?.stage ?: UiText.res(R.string.scan_notification_starting)).asString(),
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        current?.count?.let { Text(it.asString(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
        current?.rate?.let {
            Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Wavy and indeterminate until the amount of work is known, then filling up; only this redraws per tick. */
@Composable
private fun StageProgressBar(progress: () -> ScanProgressUi?) {
    val determinate by remember(progress) { derivedStateOf { progress()?.fraction != null } }
    AppProgressBar(progress = if (determinate) ({ progress()?.fraction ?: 0f }) else null)
}

@Composable
private fun DonePanel(result: ScanResultUi, onIntent: (ScanIntent) -> Unit) {
    StateMessage(
        icon = if (result.isError) Icons.Outlined.ErrorOutline else Icons.Outlined.TaskAlt,
        title = result.title.asString(),
        subtitle = result.body.asString(),
        isError = result.isError,
        action = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
            ) {
                when {
                    result.isError -> AppButton(stringResource(R.string.action_retry), { onIntent(ScanIntent.StartClicked) })
                    result.hasResults -> {
                        AppButton(stringResource(R.string.scan_action_scan_again), { onIntent(ScanIntent.ScanAgainClicked) }, kind = ButtonKind.Text)
                        AppButton(stringResource(R.string.scan_action_view_results), { onIntent(ScanIntent.ViewResultsClicked) })
                    }
                    else -> AppButton(stringResource(R.string.scan_action_scan_again), { onIntent(ScanIntent.ScanAgainClicked) })
                }
            }
        },
    )
}

@Preview
@Composable
private fun ScanSetupPreview() {
    DupeDiggerTheme(dynamicColor = false) {
        ScanScreen(
            state = ScanState(
                phase = ScanPhase.Setup,
                presets = persistentListOf(
                    PresetUi(1, UiText.Raw("Images only"), UiText.Raw("Whole device · Images"), enabled = true, hint = null, editable = true),
                    PresetUi(-2, UiText.Raw("Camera"), UiText.Raw("1 folder on your storage · 2 file types"), enabled = true, hint = null, editable = false),
                ),
            ),
            progress = { null },
            onIntent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun ScanRunningPreview() {
    DupeDiggerTheme(dynamicColor = false) {
        ScanScreen(
            state = ScanState(phase = ScanPhase.Running),
            progress = { ScanProgressUi(UiText.Raw("Comparing files…"), UiText.Raw("120 of 500"), UiText.Raw("45 files/s"), 0.24f) },
            onIntent = {},
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
        )
    }
}
