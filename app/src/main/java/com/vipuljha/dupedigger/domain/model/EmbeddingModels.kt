package com.vipuljha.dupedigger.domain.model

enum class ModelCapability { TEXT, VISION, AUDIO }

/**
 * One downloadable EmbeddingGemma file (D-006), pinned to a Hugging Face revision and checked by SHA-256.
 * [peakRamMb] is the measured peak process RSS on the CPU backend; null where it was never measured.
 */
data class ModelVariant(
    val id: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val capabilities: Set<ModelCapability>,
    val peakRamMb: Int?,
)

object ModelCatalog {
    private fun url(repo: String, revision: String, file: String) =
        "https://huggingface.co/litert-community/$repo/resolve/$revision/$file"

    val variants: List<ModelVariant> = listOf(
        ModelVariant(
            id = "embeddinggemma-270m-text",
            fileName = "embeddinggemma-2-text-270m.litertlm",
            url = url("embeddinggemma-2-text-270m-litert-lm", "9be6e8b90982095dc05c2bd162e4b954ee4dbac7", "embeddinggemma-2-text-270m.litertlm"),
            sizeBytes = 164_626_432,
            sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
            capabilities = setOf(ModelCapability.TEXT),
            peakRamMb = 522,
        ),
        ModelVariant(
            id = "embeddinggemma-440m-text-vision",
            fileName = "embeddinggemma-2-text-vision-440m.litertlm",
            url = url("embeddinggemma-2-text-vision-440m-litert-lm", "e301f74d5551b0c2641bd5cb4652a76239d5c5f8", "embeddinggemma-2-text-vision-440m.litertlm"),
            sizeBytes = 387_710_976,
            sha256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0",
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION),
            peakRamMb = null,
        ),
        ModelVariant(
            id = "embeddinggemma-740m-all",
            fileName = "embeddinggemma-2-740m.litertlm",
            url = url("embeddinggemma-2-740m-litert-lm", "94408f31843f8bd0adb11c262292627287f5fda4", "embeddinggemma-2-740m.litertlm"),
            sizeBytes = 484_622_336,
            sha256 = "e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c",
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION, ModelCapability.AUDIO),
            peakRamMb = 1029,
        ),
    )

    fun byId(id: String): ModelVariant? = variants.firstOrNull { it.id == id }
}

/** Why a model download stopped for good: a corrupt file, no room for it, or the server refusing it. */
enum class ModelDownloadFailure { CHECKSUM, NO_SPACE, HTTP, NETWORK }

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState

    /** Waiting to start, for example for Wi-Fi. */
    data object Queued : ModelDownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState

    /** Present and verified against its checksum. */
    data object Downloaded : ModelDownloadState
    data class Failed(val reason: ModelDownloadFailure) : ModelDownloadState
}
