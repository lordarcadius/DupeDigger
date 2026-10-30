package com.vipuljha.dupedigger.presentation.ai

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.ModelCapability
import com.vipuljha.dupedigger.domain.model.ModelDownloadFailure
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import kotlinx.collections.immutable.toImmutableList

fun aiUi(settings: AppSettings, models: List<Pair<ModelVariant, ModelDownloadState>>): AiUi {
    val inFlight = models.any { (_, state) -> state is ModelDownloadState.Queued || state is ModelDownloadState.Downloading }
    return AiUi(
        enabled = settings.semanticEnabled && settings.aiModelId != null,
        canEnable = settings.aiModelId != null,
        backend = settings.aiBackend,
        threshold = settings.semanticThreshold.coerceIn(AppSettings.SemanticChoices),
        wifiOnly = settings.modelDownloadWifiOnly,
        chargingOnly = settings.aiOnlyWhileCharging,
        models = models.map { (variant, state) -> variant.toRow(state, settings, inFlight) }.toImmutableList(),
    )
}

private fun ModelVariant.toRow(state: ModelDownloadState, settings: AppSettings, inFlight: Boolean) = ModelRowUi(
    id = id,
    title = UiText.res(
        when {
            ModelCapability.AUDIO in capabilities -> R.string.ai_model_all
            ModelCapability.VISION in capabilities -> R.string.ai_model_vision
            else -> R.string.ai_model_text
        },
    ),
    capabilities = UiText.res(
        when {
            ModelCapability.AUDIO in capabilities -> R.string.ai_model_all_body
            ModelCapability.VISION in capabilities -> R.string.ai_model_vision_body
            else -> R.string.ai_model_text_body
        },
    ),
    details = UiText.res(
        R.string.ai_model_details,
        formatBytes(sizeBytes),
        peakRamMb?.let { UiText.res(R.string.ai_model_memory, formatBytes(it * 1_000_000L)) } ?: UiText.res(R.string.ai_model_memory_unknown),
    ),
    status = when (state) {
        ModelDownloadState.NotDownloaded -> ModelStatusUi.NotDownloaded
        ModelDownloadState.Queued -> ModelStatusUi.Queued(UiText.res(if (settings.modelDownloadWifiOnly) R.string.ai_waiting_wifi else R.string.ai_waiting))
        is ModelDownloadState.Downloading -> ModelStatusUi.Downloading(
            fraction = if (state.totalBytes > 0) state.downloadedBytes.toFloat() / state.totalBytes else 0f,
            label = UiText.res(R.string.ai_downloading, formatBytes(state.downloadedBytes), formatBytes(state.totalBytes)),
        )
        ModelDownloadState.Downloaded -> ModelStatusUi.Downloaded
        is ModelDownloadState.Failed -> ModelStatusUi.Failed(UiText.res(state.reason.message()))
    },
    chosen = settings.aiModelId == id,
    canDownload = !inFlight,
)

private fun ModelDownloadFailure.message(): Int = when (this) {
    ModelDownloadFailure.CHECKSUM -> R.string.ai_failed_checksum
    ModelDownloadFailure.NO_SPACE -> R.string.ai_failed_space
    ModelDownloadFailure.HTTP, ModelDownloadFailure.NETWORK -> R.string.ai_failed_network
}
