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
}
