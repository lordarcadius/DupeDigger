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
    val name: String = "",
)

/** Which saved groups to list; null means any. */
data class GroupFilter(val kind: MediaKind? = null, val tier: MatchTier? = null)

/** A saved group with at least two members, and the `sourceUri` the user chose to keep, if any. */
data class GroupWithMembers(
    val groupId: Long,
    val fingerprint: String,
    val tier: MatchTier,
    val kind: MediaKind,
    val members: ImmutableList<GroupMember>,
    val keepChoice: String?,
)

/** A group split into the file to keep and the files that could go. */
data class DuplicateGroupItem(
    val groupId: Long,
    val fingerprint: String,
    val tier: MatchTier,
    val kind: MediaKind,
    val keeper: GroupMember,
    val removable: ImmutableList<GroupMember>,
) {
    val reclaimableBytes: Long get() = removable.sumOf { it.size }
}
