package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.DeviceSoc
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import javax.inject.Inject

/** Which model file a scan should run, and on which backend. */
data class EmbeddingTarget(val variant: ModelVariant, val backend: EmbeddingBackend)

/**
 * Turns the chosen model and the preferred backend into the file that actually exists on this device.
 * An NPU run needs a build compiled for this exact chip, downloaded, and the vendor dispatch library that only a
 * bundle from Play delivers. Short of all three the scan runs the portable file on the GPU rather than not running
 * at all, because the backend is a preference (D-034). Deciding here, before anything is embedded, is what keeps the
 * vectors' key honest about the backend that produced them.
 */
class ResolveEmbeddingModelUseCase @Inject constructor(
    private val downloads: ModelDownloads,
    private val soc: DeviceSoc,
) {
    operator fun invoke(variant: ModelVariant, backend: EmbeddingBackend): EmbeddingTarget {
        if (backend != EmbeddingBackend.NPU) return EmbeddingTarget(variant, backend)
        val build =
            npuBuild(variant)?.takeIf { downloads.installedPath(it) != null && soc.hasNpuRuntime() }
                ?: return EmbeddingTarget(variant, EmbeddingBackend.GPU)
        return EmbeddingTarget(build, EmbeddingBackend.NPU)
    }

    /** The build for this chip, whether or not it is downloaded; null when the chip has none. */
    fun npuBuild(variant: ModelVariant): ModelVariant? = ModelCatalog.npuBuild(variant, soc.model())
}
