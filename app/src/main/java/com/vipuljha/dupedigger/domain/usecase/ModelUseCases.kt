package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Starts downloading [ModelVariant], honouring the Wi-Fi-only setting at the time it is queued. */
class DownloadModelUseCase @Inject constructor(
    private val downloads: ModelDownloads,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(variant: ModelVariant) = downloads.start(variant, settings.settings.first().modelDownloadWifiOnly)
}
