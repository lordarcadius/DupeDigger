package com.vipuljha.dupedigger.domain.model

enum class ScanStage { INDEXING, EXACT }

/** [total] is null while the amount of work is still unknown. */
data class ScanProgress(val stage: ScanStage, val done: Int, val total: Int?)

/** INTERRUPTED marks a run whose process died before it could record an end. */
enum class ScanStatus { RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

data class ScanSummary(
    val index: IndexStats,
    val exactGroups: Int,
    val duplicateFiles: Int,
    val reclaimableBytes: Long,
    val hashesComputed: Int,
    val unreadable: Int,
)
