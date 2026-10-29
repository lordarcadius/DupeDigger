package com.vipuljha.dupedigger.domain.detector

import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import kotlinx.collections.immutable.toImmutableList

/**
 * The member to keep by default, best first by:
 * - higher quality (pixels or bitrate), only when it is measured for every member, so files hashed before quality
 *   was recorded don't skew the pick;
 * - original location: camera folders over messaging, download and cache copies, others in between;
 * - oldest modification time, with an unknown time ranked last;
 * - then `sourceUri`, so the same group always gets the same keeper.
 */
fun rankKeeper(members: List<GroupMember>): GroupMember =
    members.minWith(if (members.all { it.quality != null }) qualityFirst.then(keeperOrder) else keeperOrder)

/** The user's saved choice while that file is still a member, otherwise the ranking's pick. */
fun keeperOf(members: List<GroupMember>, chosenSourceUri: String?): GroupMember =
    members.firstOrNull { it.sourceUri == chosenSourceUri } ?: rankKeeper(members)

fun GroupWithMembers.toItem(): DuplicateGroupItem {
    val keeper = keeperOf(members, keepChoice)
    return DuplicateGroupItem(groupId, fingerprint, tier, kind, keeper, members.filter { it.fileId != keeper.fileId }.toImmutableList(), score)
}

private val qualityFirst: Comparator<GroupMember> = compareByDescending { it.quality }

private val keeperOrder: Comparator<GroupMember> =
    compareByDescending<GroupMember> { locationScore(it.path) }
        .thenBy { if (it.mtime > 0) it.mtime else Long.MAX_VALUE }
        .thenBy { it.sourceUri }

private val COPY_FOLDERS = setOf("whatsapp", "telegram", "download", "downloads", "messenger", ".thumbnails", "cache")
private const val ORIGINAL_FOLDER = "dcim"

private fun locationScore(path: String?): Int {
    if (path == null) return 1
    val folders = path.lowercase().split('/').dropLast(1)
    val isCopy = folders.any { it in COPY_FOLDERS } ||
        folders.windowed(2).any { (parent, child) -> parent == "android" && child == "media" }
    return when {
        isCopy -> 0
        ORIGINAL_FOLDER in folders -> 2
        else -> 1
    }
}
