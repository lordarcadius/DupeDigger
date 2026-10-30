package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableList

/** A file seen in a scan with whatever hashes are cached for its current size and mtime. */
data class IndexedFile(
    val id: Long,
    val sourceUri: String,
    val size: Long,
    val mtime: Long,
    val partialHash: String?,
    val fullHash: String?,
)

/** [full] is set when the partial read already covered the whole file. */
data class ContentHashes(val partial: String, val full: String?)

/** Byte-identical files; [fileIds] are sorted so the same set always reads the same. */
data class ExactGroup(val size: Long, val fullHash: String, val fileIds: ImmutableList<Long>)

data class ExactScanResult(
    val groups: ImmutableList<ExactGroup>,
    val partialHashed: Int,
    val fullHashed: Int,
    val unreadable: Int,
)
