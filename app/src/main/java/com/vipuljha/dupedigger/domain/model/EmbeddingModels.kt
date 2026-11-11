package com.vipuljha.dupedigger.domain.model

enum class ModelCapability { TEXT, VISION, AUDIO }

/**
 * One downloadable EmbeddingGemma file (D-006), pinned to a Hugging Face revision and checked by SHA-256.
 * [peakRamMb] is the measured peak process RSS on the CPU backend; null where it was never measured.
 *
 * An NPU build is a separate file compiled ahead of time for one chip, so it is a variant of its own:
 * [baseId] is the model the user chose and [socTarget] the chip it was built for. Because its [id] differs, its
 * vectors are stored under their own key and never compared with the portable build's.
 */
data class ModelVariant(
    val id: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val capabilities: Set<ModelCapability>,
    val peakRamMb: Int?,
    val baseId: String = id,
    val socTarget: String? = null,
)

/** One chip's ahead-of-time build of a model, as the model repository publishes it. */
data class NpuBuild(
    val socTarget: String,
    val fileSuffix: String,
    val sizeBytes: Long,
    val sha256: String
)

object ModelCatalog {
    private fun url(repo: String, revision: String, file: String) =
        "https://huggingface.co/litert-community/$repo/resolve/$revision/$file"

    val variants: List<ModelVariant> = listOf(
        ModelVariant(
            id = "embeddinggemma-270m-text",
            fileName = "embeddinggemma-2-text-270m.litertlm",
            url = url(
                "embeddinggemma-2-text-270m-litert-lm",
                "9be6e8b90982095dc05c2bd162e4b954ee4dbac7",
                "embeddinggemma-2-text-270m.litertlm"
            ),
            sizeBytes = 164_626_432,
            sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
            capabilities = setOf(ModelCapability.TEXT),
            peakRamMb = 522,
        ),
        ModelVariant(
            id = "embeddinggemma-440m-text-vision",
            fileName = "embeddinggemma-2-text-vision-440m.litertlm",
            url = url(
                "embeddinggemma-2-text-vision-440m-litert-lm",
                "e301f74d5551b0c2641bd5cb4652a76239d5c5f8",
                "embeddinggemma-2-text-vision-440m.litertlm"
            ),
            sizeBytes = 387_710_976,
            sha256 = "92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0",
            capabilities = setOf(ModelCapability.TEXT, ModelCapability.VISION),
            peakRamMb = null,
        ),
        ModelVariant(
            id = "embeddinggemma-740m-all",
            fileName = "embeddinggemma-2-740m.litertlm",
            url = url(
                "embeddinggemma-2-740m-litert-lm",
                "94408f31843f8bd0adb11c262292627287f5fda4",
                "embeddinggemma-2-740m.litertlm"
            ),
            sizeBytes = 484_622_336,
            sha256 = "e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c",
            capabilities = setOf(
                ModelCapability.TEXT,
                ModelCapability.VISION,
                ModelCapability.AUDIO
            ),
            peakRamMb = 1029,
        ),
    )

    fun byId(id: String): ModelVariant? = variants.firstOrNull { it.id == id }

    /** Where a model's ahead-of-time NPU builds live; a later revision than the base file, which landed first. */
    private data class NpuSource(
        val repo: String,
        val revision: String,
        val stem: String,
        val builds: List<NpuBuild>
    )

    private fun npu(socTarget: String, vendorPrefix: String, sizeBytes: Long, sha256: String) =
        NpuBuild(socTarget, "${vendorPrefix}_$socTarget", sizeBytes, sha256)

    /**
     * Only the chips LiteRT publishes an **Android** build for; the Intel PTL files in the same repositories are for
     * Windows, and the 740m repository's uncompiled NPU file isn't a device build at all.
     */
    private val npuSources = mapOf(
        "embeddinggemma-270m-text" to NpuSource(
            repo = "embeddinggemma-2-text-270m-litert-lm",
            revision = "9be6e8b90982095dc05c2bd162e4b954ee4dbac7",
            stem = "embeddinggemma-2-text-270m",
            builds = listOf(
                npu(
                    "SM8550",
                    "Qualcomm",
                    207_896_576,
                    "6a5043146940e738cfa9111aa445b1cdb3dc7bbef30caef367622f14ada1a895"
                ),
                npu(
                    "SM8650",
                    "Qualcomm",
                    207_798_272,
                    "7dabcb17eed982d6de4435aaf29cc56147a627f6ab20a1e37aadd7af5399457b"
                ),
                npu(
                    "SM8750",
                    "Qualcomm",
                    187_596_800,
                    "b5615709768db4ca4fb4f04514e7e41b0680ccb12dd5f90371193f7fd12e7c99"
                ),
                npu(
                    "SM8850",
                    "Qualcomm",
                    202_293_248,
                    "72992b649c812ae9e30ffa9ad88fc662e649f7b643500d28051fbd457ad0d22a"
                ),
                npu(
                    "QCS8275",
                    "Qualcomm",
                    208_011_264,
                    "128c32151613a5bbf8b5695b2d9ae271057338febbf06e3aeebf29e1353cb51d"
                ),
                npu(
                    "MT6991",
                    "MediaTek",
                    416_350_208,
                    "7acc8e334d882216207c23b7a477179d5af530d67fd8e2cb50ab42d3457f90c0"
                ),
                npu(
                    "MT6993",
                    "MediaTek",
                    385_925_120,
                    "eff006451073498117f454c7ab27f274b5fe14a54af49be239e8f480ee49fa36"
                ),
                npu(
                    "Tensor_G5",
                    "Google",
                    166_007_264,
                    "f066663433a31a8228b46f3271d2714a3b72a341ed4563cbc6d90f2e3dd0162d"
                ),
                npu(
                    "Tensor_G6",
                    "Google",
                    199_532_256,
                    "4cd282395a7b611ec8dec95160cd31a1f471a7b1cde1fc71717aa539c2a29883"
                ),
            ),
        ),
        "embeddinggemma-440m-text-vision" to NpuSource(
            repo = "embeddinggemma-2-text-vision-440m-litert-lm",
            revision = "538e6574db281b4fe17993a3212f10c73bd678c6",
            stem = "embeddinggemma-2-text-vision-440m",
            builds = listOf(
                npu(
                    "SM8550",
                    "Qualcomm",
                    465_993_728,
                    "4427eecca4289555e72b297308f4c9cf97110a657de25dd2fe7946ac824f6214"
                ),
                npu(
                    "SM8650",
                    "Qualcomm",
                    465_747_968,
                    "ec7d2ee513830337343567409f32fea4a3e4f8f7b94facd0a20f3d3523cd7480"
                ),
                npu(
                    "SM8750",
                    "Qualcomm",
                    446_611_456,
                    "c5c05cb59833541a2d0d9374c7b50d2c3a406a4a84c85762775e5f9c6d96cd0c"
                ),
                npu(
                    "SM8850",
                    "Qualcomm",
                    462_536_704,
                    "661369235aa9fc23cf04a213d4e445c4634073832279f5a261ed22650a8ebc5b"
                ),
                npu(
                    "QCS8275",
                    "Qualcomm",
                    465_977_344,
                    "ce8ccce80a12f6bc9acfe664b76c3cc46a698f0da81ae857f706b06b9a4c7d38"
                ),
                npu(
                    "MT6991",
                    "MediaTek",
                    798_097_408,
                    "53cb4b8b3d8ca3e022ceec1dd809ddd26c281721c4652e1fadc82d2433cbc5de"
                ),
                npu(
                    "MT6993",
                    "MediaTek",
                    767_770_624,
                    "1cfc52d73266fe0e099e853b816150c22deb32fea421cabf9647dc8d8112ede9"
                ),
                npu(
                    "Tensor_G5",
                    "Google",
                    559_418_036,
                    "96cdbae7b3dd6fa0f829fef2d0dd5bafc0179890d9bafbfe972db852e818d758"
                ),
                npu(
                    "Tensor_G6",
                    "Google",
                    513_166_004,
                    "40e98aeed9ef08abfd91d5d309f64f4fa06195c513931e917bcf5ea41a910668"
                ),
            ),
        ),
        "embeddinggemma-740m-all" to NpuSource(
            repo = "embeddinggemma-2-740m-litert-lm",
            revision = "66011bd4f00a8b36a1d3c4ae7bf908b4891b4881",
            stem = "embeddinggemma-2-740m",
            builds = listOf(
                npu(
                    "SM8550",
                    "Qualcomm",
                    562_905_088,
                    "802686a865fd7350f2ce4b6fa17ce02a411f56b5b508ec2bd8cb7310b2db82e6"
                ),
                npu(
                    "SM8650",
                    "Qualcomm",
                    562_659_328,
                    "3b8a18c6d043b8132c4eda45611296e1ae455ada82cda8b591ec0ca141e041c5"
                ),
                npu(
                    "SM8750",
                    "Qualcomm",
                    543_522_816,
                    "ac1973c0195dc6e28a621651f2f5e04d35fdc7a7b11cdbbe94f29f5dccb57996"
                ),
                npu(
                    "SM8850",
                    "Qualcomm",
                    559_448_064,
                    "19f6e8db6aa74b116d1c558c862ee131f60ce093576d3c8dd1898f656ca8080b"
                ),
                npu(
                    "QCS8275",
                    "Qualcomm",
                    562_888_704,
                    "4d99a14e63e01cd2947961a1b4939395ad50d3a101a411e05f8397f4a80dbd80"
                ),
                npu(
                    "MT6991",
                    "MediaTek",
                    895_008_768,
                    "99e6a35b824b4f7ad4f7c7f20c59ba380aa90e0b4442aadfee3674230c6c7cca"
                ),
                npu(
                    "MT6993",
                    "MediaTek",
                    864_681_984,
                    "ed2a2554bfb6d7abcafd0e9e36ee2f8d7de82888e7ed0fc52dd144e65b171742"
                ),
                npu(
                    "Tensor_G5",
                    "Google",
                    657_656_500,
                    "5b53d76c2c7c66d2dc6230ff158ea0033ac5939ac7cfcf827c3a22cdfc742a5e"
                ),
                npu(
                    "Tensor_G6",
                    "Google",
                    615_713_460,
                    "ed0e4f413e705946eda544078d8bf5acf10fe5aee2946451c81919a1f59665e7"
                ),
            ),
        ),
    )

    /** Every chip any model has an NPU build for, by [socKey]; what the device targeting config must also cover. */
    val npuSocKeys: Set<String> =
        npuSources.values.flatMapTo(mutableSetOf()) { source -> source.builds.map { socKey(it.socTarget) } }

    /** [variant] compiled ahead of time for [soc] (a raw `Build.SOC_MODEL`), or null when that chip has no build. */
    fun npuBuild(variant: ModelVariant, soc: String): ModelVariant? {
        val source = npuSources[variant.baseId] ?: return null
        val key = socKey(soc)
        val build = source.builds.firstOrNull { socKey(it.socTarget) == key } ?: return null
        return variant.copy(
            id = "${variant.baseId}$NPU_ID_SEPARATOR${build.socTarget}",
            fileName = "${source.stem}_${build.fileSuffix}.litertlm",
            url = url(source.repo, source.revision, "${source.stem}_${build.fileSuffix}.litertlm"),
            sizeBytes = build.sizeBytes,
            sha256 = build.sha256,
            peakRamMb = null,
            baseId = variant.baseId,
            socTarget = build.socTarget,
        )
    }
}

/** Separates a model from the chip it was built for in a variant id; also what tells the two kinds of key apart. */
const val NPU_ID_SEPARATOR = "+"

/** Chip names are compared without case or punctuation, so "Tensor G5", "tensor_g5" and "TENSORG5" are one chip. */
fun socKey(soc: String): String = soc.uppercase().filter { it.isLetterOrDigit() }

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
