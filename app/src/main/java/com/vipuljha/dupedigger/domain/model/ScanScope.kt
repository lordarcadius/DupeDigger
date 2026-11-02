package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A folder the user picked through SAF; [path] is set when it maps to local storage. */
data class ScanFolder(val uri: String, val name: String, val path: String?)

sealed interface ScanScope {
    /** Every file on the device; needs All files access. */
    data object WholeDevice : ScanScope

    /** Photos, videos and audio through MediaStore; needs media access. */
    data object MediaOnly : ScanScope

    data class Folders(val folders: ImmutableList<ScanFolder>) : ScanScope

    /** Folders such as `DCIM/Camera`, relative to the root of every storage volume; needs All files access. */
    data class Paths(val paths: ImmutableList<String>) : ScanScope
}

enum class ScopeKind { WHOLE_DEVICE, MEDIA_ONLY, FOLDERS, PATHS }

val ScanScope.kind: ScopeKind
    get() = when (this) {
        ScanScope.WholeDevice -> ScopeKind.WHOLE_DEVICE
        ScanScope.MediaOnly -> ScopeKind.MEDIA_ONLY
        is ScanScope.Folders -> ScopeKind.FOLDERS
        is ScanScope.Paths -> ScopeKind.PATHS
    }

/**
 * The folders a [ScanScope.Paths] walks, shortest first.
 * - Blank segments are dropped; a path with a `.` or `..` segment is ignored, so a walk never leaves its volume.
 * - A path equal to or inside another is dropped, ignoring case as shared storage does, so no file is indexed twice.
 */
fun ScanScope.Paths.roots(): ImmutableList<String> {
    val kept = mutableListOf<String>()
    paths.mapNotNull(::cleanRelativePath).sortedBy { it.length }.forEach { path ->
        if (kept.none { path.equals(it, ignoreCase = true) || path.startsWith("$it/", ignoreCase = true) }) kept += path
    }
    return kept.toImmutableList()
}

private fun cleanRelativePath(path: String): String? {
    val segments = path.split('/').filter { it.isNotEmpty() }
    return if (segments.isEmpty() || segments.any { it == "." || it == ".." }) null else segments.joinToString("/")
}

/** The scopes the scan picker may offer, widest first; it never offers one the app can't read. */
fun availableScopes(access: StorageAccessState, folders: List<ScanFolder>): ImmutableList<ScanScope> = buildList {
    if (access.allFiles) add(ScanScope.WholeDevice)
    if (access.media != MediaAccess.NONE) add(ScanScope.MediaOnly)
    if (folders.isNotEmpty()) add(ScanScope.Folders(folders.toImmutableList()))
}.toImmutableList()

/** Whether this scope can still be read; a scan of an unreadable scope would find nothing and prune its whole index. */
fun ScanScope.isReachable(access: StorageAccessState, folders: List<ScanFolder>): Boolean = when (this) {
    ScanScope.WholeDevice -> access.allFiles
    ScanScope.MediaOnly -> access.media != MediaAccess.NONE
    is ScanScope.Folders -> this.folders.isNotEmpty() && this.folders.all { picked -> folders.any { it.uri == picked.uri } }
    is ScanScope.Paths -> access.allFiles && roots().isNotEmpty()
}

/** Whether this scope can be read now, judged from what [availableScopes] returned for the same access and folders. */
fun ScanScope.isReachableWith(available: List<ScanScope>): Boolean = when (this) {
    ScanScope.WholeDevice, ScanScope.MediaOnly -> this in available
    is ScanScope.Folders -> {
        val picked = available.filterIsInstance<ScanScope.Folders>().flatMap { scope -> scope.folders.map { it.uri } }
        folders.isNotEmpty() && folders.all { it.uri in picked }
    }
    is ScanScope.Paths -> ScanScope.WholeDevice in available && roots().isNotEmpty()
}
