package com.vipuljha.dupedigger.domain.detector

import com.vipuljha.dupedigger.domain.model.GroupMember

/**
 * The member to keep by default, best first by:
 * - higher quality (resolution or bitrate), when measured;
 * - original location: camera folders over messaging, download and cache copies, others in between;
 * - oldest modification time, with an unknown time ranked last;
 * - then `sourceUri`, so the same group always gets the same keeper.
 */
fun rankKeeper(members: List<GroupMember>): GroupMember = members.minWith(keeperOrder)

private val keeperOrder: Comparator<GroupMember> =
    compareByDescending<GroupMember> { it.quality ?: -1L }
        .thenByDescending { locationScore(it.path) }
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
