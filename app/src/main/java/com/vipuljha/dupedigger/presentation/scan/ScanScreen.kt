package com.vipuljha.dupedigger.presentation.scan

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppMotion
import com.vipuljha.dupedigger.core.designsystem.AppProgressBar
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.SectionHeader
import com.vipuljha.dupedigger.core.designsystem.StateMessage
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
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onBack = onBack) { TopBarTitle(stringResource(R.string.scan_title)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Crossfade(
            targetState = state.phase,
            modifier = Modifier.fillMaxSize().padding(padding),
            animationSpec = tween(AppMotion.StateFadeMillis, easing = FastOutSlowInEasing),
            label = "scanPhase",
        ) { phase ->
            when (phase) {
                ScanPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
                ScanPhase.Setup -> SetupPanel(state, onIntent)
                ScanPhase.Running -> RunningPanel(progress, state.cancelling) { onIntent(ScanIntent.CancelClicked) }
                is ScanPhase.Done -> DonePanel(phase.result, onIntent)
            }
        }
    }
}

@Composable
private fun SetupPanel(state: ScanState, onIntent: (ScanIntent) -> Unit) {
    if (state.scopes.isEmpty()) {
        StateMessage(
            icon = Icons.Outlined.FolderOff,
            title = stringResource(R.string.scan_no_scope_title),
            subtitle = stringResource(R.string.scan_no_scope_body),
        )
        return
    }
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.maxWidthReading).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Dimens.listEndSpace),
        ) {
            item(key = "scope-header", contentType = "header") { SectionHeader(stringResource(R.string.scan_scope_title)) }
            items(state.scopes, key = { it.kind }, contentType = { "scope" }) { option ->
                ScopeRow(option, selected = option.kind == state.selectedScope) { onIntent(ScanIntent.ScopeSelected(option.kind)) }
            }
            item(key = "tier-header", contentType = "header") { SectionHeader(stringResource(R.string.scan_tier_title)) }
            item(key = "tier-exact", contentType = "tier") {
                TierRow(stringResource(R.string.scan_tier_exact), stringResource(R.string.scan_tier_exact_body), checked = true, enabled = false) {}
            }
            item(key = "tier-near", contentType = "tier") {
                TierRow(stringResource(R.string.scan_tier_near), stringResource(R.string.scan_tier_near_body), state.nearEnabled, enabled = true) {
                    onIntent(ScanIntent.NearToggled(it))
                }
            }
            item(key = "tier-ai", contentType = "tier") {
                TierRow(
                    stringResource(R.string.scan_tier_ai),
                    stringResource(if (state.aiAvailable) R.string.scan_tier_ai_on_body else R.string.scan_tier_ai_body),
                    state.aiEnabled,
                    enabled = state.aiAvailable,
                ) { onIntent(ScanIntent.AiToggled(it)) }
            }
            item(key = "start", contentType = "start") {
                AppButton(
                    label = stringResource(R.string.scan_action_start),
                    onClick = { onIntent(ScanIntent.StartClicked) },
                    loading = state.starting,
                    enabled = state.selectedScope != null,
                    modifier = Modifier.padding(top = Dimens.spaceXl),
                )
            }
        }
    }
}

@Composable
private fun ScopeRow(option: ScopeOptionUi, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowComfortableMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Dimens.spaceLg))
        RowText(option.title.asString(), option.body.asString(), Modifier.weight(1f))
    }
}

@Composable
private fun TierRow(title: String, body: String, checked: Boolean, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
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

@Composable
private fun RunningPanel(progress: () -> ScanProgressUi?, cancelling: Boolean, onCancel: () -> Unit) {
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
            AppButton(stringResource(R.string.scan_action_cancel_scan), onCancel, kind = ButtonKind.Outlined, loading = cancelling)
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
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm), verticalAlignment = Alignment.CenterVertically) {
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
                scopes = persistentListOf(
                    ScopeOptionUi(ScopeKind.MEDIA_ONLY, UiText.Raw("Photos, videos and audio"), UiText.Raw("Everything in your media library.")),
                    ScopeOptionUi(ScopeKind.FOLDERS, UiText.Raw("Picked folders"), UiText.Raw("2 folders")),
                ),
                selectedScope = ScopeKind.MEDIA_ONLY,
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
