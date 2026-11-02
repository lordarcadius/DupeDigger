package com.vipuljha.dupedigger.domain.model

/** Counts from one indexing pass; only [added] and [changed] files need hashing again. */
data class IndexStats(
    val added: Int = 0,
    val changed: Int = 0,
    val unchanged: Int = 0,
    val removed: Int = 0,
) {
    /** Every file the pass found, new or not. */
    val scanned: Int get() = added + changed + unchanged

    operator fun plus(other: IndexStats) = IndexStats(
        added = added + other.added,
        changed = changed + other.changed,
        unchanged = unchanged + other.unchanged,
        removed = removed + other.removed,
    )
}
