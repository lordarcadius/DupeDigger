package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Makes [ModelVariant] the AI tier's model; only a downloaded one can be chosen. */
class ChooseModelUseCase @Inject constructor(private val settings: SettingsRepository, private val downloads: ModelDownloads) {
    suspend operator fun invoke(variant: ModelVariant): Boolean {
        if (downloads.installedPath(variant) == null) return false
        settings.update { it.copy(aiModelId = variant.id) }
        return true
    }
}

/** Turns the AI tier on, which needs a chosen model, or off. */
class SetSemanticEnabledUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = settings.update { it.copy(semanticEnabled = enabled && it.aiModelId != null) }
}

class SetAiBackendUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(backend: EmbeddingBackend) = settings.update { it.copy(aiBackend = backend) }
}

/** How alike two files must be for the AI tier, within the offered range; used from the next scan. */
class SetSemanticThresholdUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(threshold: Float) =
        settings.update { it.copy(semanticThreshold = threshold.coerceIn(AppSettings.SemanticChoices)) }
}

class SetAiChargingOnlyUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(chargingOnly: Boolean) = settings.update { it.copy(aiOnlyWhileCharging = chargingOnly) }
}

/** Saves the Wi-Fi-only choice and queues a waiting or running download again so the new rule applies to it. */
class SetModelWifiOnlyUseCase @Inject constructor(private val settings: SettingsRepository, private val downloads: ModelDownloads) {
    suspend operator fun invoke(wifiOnly: Boolean) {
        settings.update { it.copy(modelDownloadWifiOnly = wifiOnly) }
        for (variant in ModelCatalog.variants) {
            val state = downloads.observe(variant).first()
            if (state is ModelDownloadState.Queued || state is ModelDownloadState.Downloading) downloads.restart(variant, wifiOnly)
        }
    }
}

class CancelModelDownloadUseCase @Inject constructor(private val downloads: ModelDownloads) {
    operator fun invoke(variant: ModelVariant) = downloads.cancel(variant)
}

/**
 * Removes a model and everything made with it: the file and its stored vectors. If it was the chosen model the AI tier
 * turns off, so nothing is left half-set; semantic groups from earlier scans go with the next scan.
 */
class DeleteModelUseCase @Inject constructor(
    private val downloads: ModelDownloads,
    private val settings: SettingsRepository,
    private val fileIndex: FileIndexRepository,
) {
    suspend operator fun invoke(variant: ModelVariant) {
        downloads.cancel(variant)
        downloads.delete(variant)
        fileIndex.deleteEmbeddings(variant.id)
        settings.update { if (it.aiModelId == variant.id) it.copy(aiModelId = null, semanticEnabled = false) else it }
    }
}
