package com.vipuljha.dupedigger.domain.model

/** App and scan settings; the exact tier always runs. Thresholds apply at query time, so changing them never forces a rehash. */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val nearDuplicatesEnabled: Boolean = true,
    val semanticEnabled: Boolean = false,
    val nearHammingThreshold: Int = 10,
    val semanticThreshold: Float = 0.90f,
    val minFileSizeBytes: Long = 1L,
    val skipHiddenFiles: Boolean = true,
    val modelDownloadWifiOnly: Boolean = true,
    /** The downloaded model the AI tier uses; null until one is chosen. */
    val aiModelId: String? = null,
    val aiBackend: EmbeddingBackend = EmbeddingBackend.CPU,
    /** Run the AI tier only while the device charges; scans on battery skip it. */
    val aiOnlyWhileCharging: Boolean = false,
) {
    companion object {
        val HammingRange = 0..64

        /** The near-match threshold the UI offers; only 10 was timed, and wider bands slow grouping toward all-pairs (D-012). */
        val NearThresholdChoices = 4..12
        val SemanticRange = 0f..1f

        /** The AI threshold the UI offers: unrelated photos scored up to 0.88 in the golden set (D-006). */
        val SemanticChoices = 0.88f..0.98f
    }
}
