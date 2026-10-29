package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableList

enum class MatchTier { EXACT, NEAR, SEMANTIC }

/**
 * The identity of an exact group across rescans: its content, not its members, so moves and new copies keep it.
 * Saved ignore and keep choices are keyed by it; changing the format orphans them.
 */
fun exactFingerprint(size: Long, fullHash: String): String = "${MatchTier.EXACT}:$size:$fullHash"

/** A group to save for a scan. */
data class FoundGroup(
    val fingerprint: String,
    val tier: MatchTier,
    val score: Float,
    val fileIds: ImmutableList<Long>,
)

/**
 * A group member with what keeper ranking needs.
 * [quality] is pixels for images and video or bitrate for audio, null until measured; [mtime] ≤ 0 means unknown.
 */
data class GroupMember(
    val fileId: Long,
    val sourceUri: String,
    val path: String?,
    val size: Long,
    val mtime: Long,
    val quality: Long? = null,
)
