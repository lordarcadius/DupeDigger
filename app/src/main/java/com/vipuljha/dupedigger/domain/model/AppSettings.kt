package com.vipuljha.dupedigger.domain.model

/** Scan settings; the exact tier always runs. Thresholds apply at query time, so changing them never forces a rehash. */
data class AppSettings(
    val nearDuplicatesEnabled: Boolean = true,
    val semanticEnabled: Boolean = false,
    val nearHammingThreshold: Int = 10,
    val semanticThreshold: Float = 0.90f,
    val minFileSizeBytes: Long = 1L,
    val skipHiddenFiles: Boolean = true,
) {
    companion object {
        val HammingRange = 0..64
        val SemanticRange = 0f..1f
    }
}
