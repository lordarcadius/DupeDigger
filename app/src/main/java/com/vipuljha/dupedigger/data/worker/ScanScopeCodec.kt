package com.vipuljha.dupedigger.data.worker

import androidx.work.Data
import androidx.work.workDataOf
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScopeKind
import com.vipuljha.dupedigger.domain.model.roots
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * Stores a scope and the file types to search as work input.
 * - Folders are stored as tree URIs and resolved against the current grants when read back.
 * - Paths are stored as they are.
 */
object ScanScopeCodec {
    private const val KEY_KIND = "scope_kind"
    private const val KEY_FOLDERS = "scope_folders"
    private const val KEY_PATHS = "scope_paths"
    private const val KEY_MEDIA_KINDS = "media_kinds"
    private const val WHOLE_DEVICE = "WHOLE_DEVICE"
    private const val MEDIA_ONLY = "MEDIA_ONLY"
    private const val FOLDERS = "FOLDERS"
    private const val PATHS = "PATHS"

    fun toData(scope: ScanScope, mediaKinds: Set<MediaKind>): Data = workDataOf(
        KEY_KIND to kindOf(scope),
        KEY_FOLDERS to (scope as? ScanScope.Folders)?.folders?.map { it.uri }?.toTypedArray(),
        KEY_PATHS to (scope as? ScanScope.Paths)?.paths?.toTypedArray(),
        KEY_MEDIA_KINDS to mediaKinds.map { it.name }.toTypedArray(),
    )

    /** A folder whose grant was released since the scan was queued is dropped; null for unreadable input. */
    fun fromData(data: Data, pickedFolders: List<ScanFolder>): ScanScope? =
        when (data.getString(KEY_KIND)) {
            WHOLE_DEVICE -> ScanScope.WholeDevice
            MEDIA_ONLY -> ScanScope.MediaOnly
            FOLDERS -> {
                val uris = data.getNullableStringArray(KEY_FOLDERS).orEmpty().toSet()
                ScanScope.Folders(pickedFolders.filter { it.uri in uris }.toImmutableList())
            }

            // Work data can hold a null inside a string array, and a null path names nothing to scan.
            PATHS -> ScanScope.Paths(
                data.getNullableStringArray(KEY_PATHS).orEmpty().filterNotNull().toImmutableList()
            )

            else -> null
        }

    /** Every type when none is stored, as for work queued before types could be chosen; unknown names are dropped. */
    fun mediaKindsFromData(data: Data): ImmutableSet<MediaKind> =
        data.getNullableStringArray(KEY_MEDIA_KINDS).orEmpty()
            .mapNotNull { name -> MediaKind.entries.firstOrNull { it.name == name } }
            .toImmutableSet()
            .ifEmpty { AllMediaKinds }

    /** A readable, stable name for `scan_run.scope`. */
    fun key(scope: ScanScope): String = when (scope) {
        is ScanScope.Folders -> "$FOLDERS:" + scope.folders.map { it.uri }.sorted()
            .joinToString("|")

        is ScanScope.Paths -> "$PATHS:" + scope.roots().sorted().joinToString("|")
        else -> kindOf(scope)
    }

    /** The scope kind behind a [key]; null for a key this build doesn't know. */
    fun kindOf(key: String): ScopeKind? = when (key.substringBefore(':')) {
        WHOLE_DEVICE -> ScopeKind.WHOLE_DEVICE
        MEDIA_ONLY -> ScopeKind.MEDIA_ONLY
        FOLDERS -> ScopeKind.FOLDERS
        PATHS -> ScopeKind.PATHS
        else -> null
    }

    private fun kindOf(scope: ScanScope) = when (scope) {
        ScanScope.WholeDevice -> WHOLE_DEVICE
        ScanScope.MediaOnly -> MEDIA_ONLY
        is ScanScope.Folders -> FOLDERS
        is ScanScope.Paths -> PATHS
    }
}
