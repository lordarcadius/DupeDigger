package com.vipuljha.dupedigger.domain.model

enum class ScanStage { INDEXING, EXACT }

/** [total] is null while the amount of work is still unknown. */
data class ScanProgress(val stage: ScanStage, val done: Int, val total: Int?)

/** INTERRUPTED marks a run whose process died before it could record an end. */
enum class ScanStatus { RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

/** A recorded scan; [summary] is set once it completed, and [scope] is null for a record it can't read. */
data class ScanRunInfo(
    val id: Long,
    val scope: ScopeKind?,
    val status: ScanStatus,
    val startedAt: Long,
    val finishedAt: Long?,
    val summary: ScanSummary?,
)

/** Space the saved groups could free in one kind of file: everything but the largest copy of each group. */
data class KindReclaim(val kind: MediaKind, val groups: Int, val files: Int, val bytes: Long)

data class ScanSummary(
    val index: IndexStats,
    val exactGroups: Int,
    val duplicateFiles: Int,
    val reclaimableBytes: Long,
    val hashesComputed: Int,
    val unreadable: Int,
)
