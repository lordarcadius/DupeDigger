package com.vipuljha.dupedigger.presentation.ai

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class AiState(
    val content: ContentState<AiUi> = ContentState.Loading,
    /** The model awaiting "Delete" in the confirmation dialog. */
    val confirmDelete: ModelRowUi? = null,
) : UiState

/** [canEnable] is false until a downloaded model is chosen. */
@Immutable
data class AiUi(
    val enabled: Boolean,
    val canEnable: Boolean,
    val backend: EmbeddingBackend,
    val threshold: Float,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val models: ImmutableList<ModelRowUi>,
)

/** [canDownload] is false while another model downloads, since only one runs at a time. */
@Immutable
data class ModelRowUi(
    val id: String,
    val title: UiText,
    val capabilities: UiText,
    val details: UiText,
    val status: ModelStatusUi,
    val chosen: Boolean,
    val canDownload: Boolean,
)

@Immutable
sealed interface ModelStatusUi {
    data object NotDownloaded : ModelStatusUi
    data class Queued(val label: UiText) : ModelStatusUi
    data class Downloading(val fraction: Float, val label: UiText) : ModelStatusUi
    data object Downloaded : ModelStatusUi
    data class Failed(val reason: UiText) : ModelStatusUi
}

sealed interface AiIntent : UiIntent {
    data class EnabledToggled(val enabled: Boolean) : AiIntent
    data class DownloadClicked(val id: String) : AiIntent
    data class CancelClicked(val id: String) : AiIntent
    data class UseClicked(val id: String) : AiIntent
    data class DeleteClicked(val id: String) : AiIntent
    data object DeleteConfirmed : AiIntent
    data object DeleteDismissed : AiIntent
    data class BackendSelected(val backend: EmbeddingBackend) : AiIntent
    data class ThresholdChanged(val threshold: Float) : AiIntent
    data class WifiOnlyToggled(val wifiOnly: Boolean) : AiIntent
    data class ChargingOnlyToggled(val chargingOnly: Boolean) : AiIntent
}

sealed interface AiEffect : UiEffect {
    data class ShowMessage(val message: UiText) : AiEffect
}
