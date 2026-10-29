package com.vipuljha.dupedigger.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.designsystem.AppButton
import com.vipuljha.dupedigger.core.designsystem.AppConnectedButtonGroup
import com.vipuljha.dupedigger.core.designsystem.AppDialog
import com.vipuljha.dupedigger.core.designsystem.AppLoadingIndicator
import com.vipuljha.dupedigger.core.designsystem.AppProgressBar
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.ButtonKind
import com.vipuljha.dupedigger.core.designsystem.Dimens
import com.vipuljha.dupedigger.core.designsystem.LocalPaneMetrics
import com.vipuljha.dupedigger.core.designsystem.SectionHeader
import com.vipuljha.dupedigger.core.designsystem.StateMessage
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle
import com.vipuljha.dupedigger.core.ui.StateSwitch
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.core.ui.formatSimilarity
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt

@Composable
fun AiScreen(
    state: AiState,
    onIntent: (AiIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onBack = onBack) { TopBarTitle(stringResource(R.string.ai_title)) } },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        StateSwitch(
            state = state.content,
            isEmpty = { false },
            modifier = Modifier.fillMaxSize().padding(padding),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppLoadingIndicator() } },
            empty = {},
            error = { error -> StateMessage(Icons.Outlined.ErrorOutline, stringResource(R.string.ai_error_title), error.message.asString(), isError = true) },
        ) { ui -> AiContent(ui, onIntent) }
    }
    state.confirmDelete?.let { row ->
        AppDialog(
            onDismissRequest = { onIntent(AiIntent.DeleteDismissed) },
            onEnter = { onIntent(AiIntent.DeleteConfirmed) },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text(stringResource(R.string.ai_delete_title, row.title.asString())) },
            text = { Text(stringResource(R.string.ai_delete_body)) },
            confirmButton = { AppButton(stringResource(R.string.action_delete), { onIntent(AiIntent.DeleteConfirmed) }, kind = ButtonKind.Destructive) },
            dismissButton = { AppButton(stringResource(R.string.action_cancel), { onIntent(AiIntent.DeleteDismissed) }, kind = ButtonKind.Text) },
        )
    }
}

@Composable
private fun AiContent(ui: AiUi, onIntent: (AiIntent) -> Unit) {
    val gutter = LocalPaneMetrics.current.gutter
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.maxWidthReading).fillMaxWidth(),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = Dimens.listEndSpace),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
        ) {
            item(key = "intro", contentType = "intro") {
                Column(Modifier.animateItem()) {
                    Text(stringResource(R.string.ai_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchRow(
                        title = stringResource(R.string.ai_use),
                        body = stringResource(if (ui.canEnable) R.string.ai_use_body else R.string.ai_use_body_no_model),
                        checked = ui.enabled,
                        enabled = ui.canEnable,
                    ) { onIntent(AiIntent.EnabledToggled(it)) }
                }
            }
            item(key = "models-header", contentType = "header") {
                SectionHeader(stringResource(R.string.ai_models_title), description = stringResource(R.string.ai_models_body), modifier = Modifier.animateItem())
            }
            items(ui.models, key = { it.id }, contentType = { "model" }) { row -> ModelCard(row, onIntent, Modifier.animateItem()) }
            item(key = "options", contentType = "options") { Options(ui, onIntent, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun ModelCard(row: ModelRowUi, onIntent: (AiIntent) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(Dimens.spaceLg), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
            Text(row.title.asString(), style = MaterialTheme.typography.titleMedium)
            Text(row.capabilities.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(row.details.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val status = row.status) {
                is ModelStatusUi.Downloading -> {
                    AppProgressBar(progress = { status.fraction }, modifier = Modifier.padding(top = Dimens.spaceXs))
                    Text(status.label.asString(), style = MaterialTheme.typography.bodySmall)
                }
                is ModelStatusUi.Queued -> Text(status.label.asString(), style = MaterialTheme.typography.bodySmall)
                is ModelStatusUi.Failed -> Text(status.reason.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                ModelStatusUi.Downloaded -> if (row.chosen) {
                    Text(stringResource(R.string.ai_in_use), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                ModelStatusUi.NotDownloaded -> Unit
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm, Alignment.End)) {
                when (row.status) {
                    ModelStatusUi.NotDownloaded -> AppButton(
                        stringResource(R.string.ai_download), { onIntent(AiIntent.DownloadClicked(row.id)) }, kind = ButtonKind.Tonal, enabled = row.canDownload,
                    )
                    is ModelStatusUi.Failed -> AppButton(
                        stringResource(R.string.action_retry), { onIntent(AiIntent.DownloadClicked(row.id)) }, kind = ButtonKind.Tonal, enabled = row.canDownload,
                    )
                    is ModelStatusUi.Queued, is ModelStatusUi.Downloading ->
                        AppButton(stringResource(R.string.action_cancel), { onIntent(AiIntent.CancelClicked(row.id)) }, kind = ButtonKind.Text)
                    ModelStatusUi.Downloaded -> {
                        AppButton(stringResource(R.string.action_delete), { onIntent(AiIntent.DeleteClicked(row.id)) }, kind = ButtonKind.Text)
                        if (!row.chosen) AppButton(stringResource(R.string.ai_use_model), { onIntent(AiIntent.UseClicked(row.id)) }, kind = ButtonKind.Tonal)
                    }
                }
            }
        }
    }
}

@Composable
private fun Options(ui: AiUi, onIntent: (AiIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        SectionHeader(stringResource(R.string.ai_options_title))
        SwitchRow(stringResource(R.string.ai_wifi_only), stringResource(R.string.ai_wifi_only_body), ui.wifiOnly) { onIntent(AiIntent.WifiOnlyToggled(it)) }
        SwitchRow(stringResource(R.string.ai_charging_only), stringResource(R.string.ai_charging_only_body), ui.chargingOnly) {
            onIntent(AiIntent.ChargingOnlyToggled(it))
        }
        Text(stringResource(R.string.ai_backend), style = MaterialTheme.typography.bodyLarge)
        AppConnectedButtonGroup(
            options = persistentListOf(EmbeddingBackend.CPU, EmbeddingBackend.GPU),
            isSelected = { it == ui.backend },
            onClick = { onIntent(AiIntent.BackendSelected(it)) },
            label = { stringResource(if (it == EmbeddingBackend.CPU) R.string.ai_backend_cpu else R.string.ai_backend_gpu) },
        )
        Text(stringResource(R.string.ai_backend_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ThresholdSlider(ui.threshold, onIntent)
    }
}

/** Follows the drag locally and saves once released, so dragging doesn't write the store on every step. */
@Composable
private fun ThresholdSlider(threshold: Float, onIntent: (AiIntent) -> Unit) {
    val choices = AppSettings.SemanticChoices
    var value by remember(threshold) { mutableFloatStateOf(threshold) }
    val stateText = stringResource(R.string.ai_threshold_value, formatSimilarity(value + 0.001f).asString())
    Text(stringResource(R.string.ai_threshold), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Dimens.spaceSm))
    Text(stateText, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onIntent(AiIntent.ThresholdChanged((value * 100).roundToInt() / 100f)) },
        valueRange = choices.start..choices.endInclusive,
        steps = ((choices.endInclusive - choices.start) * 100).roundToInt() - 1,
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = stateText },
    )
    Text(stringResource(R.string.ai_threshold_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.rowComfortableMin)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = Dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Dimens.spaceLg))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
