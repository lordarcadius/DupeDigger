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

/** Which member of each group a bulk selection keeps; every other member is selected. */
sealed interface KeepRule {
    /** The user's keep choice, else the default ranking. */
    data object Best : KeepRule
    data object Newest : KeepRule
    data object Oldest : KeepRule
    data object Largest : KeepRule

    /** A member inside [folder] (an absolute path, subfolders included); a group with none there keeps its best. */
    data class InFolder(val folder: String) : KeepRule
}

/** A folder holding members of the matching groups, and how many. */
data class DuplicateFolder(val path: String, val files: Int)

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
    val score: Float = 1f,
)

/** A group split into the file to keep and the files that could go. */
data class DuplicateGroupItem(
    val groupId: Long,
    val fingerprint: String,
    val tier: MatchTier,
    val kind: MediaKind,
    val keeper: GroupMember,
    val removable: ImmutableList<GroupMember>,
    val score: Float = 1f,
) {
    val reclaimableBytes: Long get() = removable.sumOf { it.size }
}

/** Dimensions read from the file itself, already turned upright; null fields weren't readable. */
data class MediaInfo(val width: Int?, val height: Int?)

/** A file as another app can open it: a shareable URI and its type. */
data class ViewTarget(val uri: String, val mimeType: String?)

/**
 * What the saved groups of one kind and tier add up to: how many groups, how many copies could go, and their bytes.
 * Both filter chips are counted from these, so a count never promises groups the other filter hides.
 */
data class GroupFacet(val kind: MediaKind, val tier: MatchTier, val groups: Int, val files: Int, val bytes: Long)

/** The facets left once the other filter is applied: [kind] or [tier] null means that filter is off. */
fun List<GroupFacet>.matching(kind: MediaKind? = null, tier: MatchTier? = null): List<GroupFacet> =
    filter { (kind == null || it.kind == kind) && (tier == null || it.tier == tier) }
