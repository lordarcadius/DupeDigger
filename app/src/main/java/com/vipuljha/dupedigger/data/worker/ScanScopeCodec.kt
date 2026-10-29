package com.vipuljha.dupedigger.data.worker

import androidx.work.Data
import androidx.work.workDataOf
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScopeKind
import kotlinx.collections.immutable.toImmutableList

/** Stores a scope as work input by kind and tree URIs; folders are resolved against the current grants when read back. */
object ScanScopeCodec {
    private const val KEY_KIND = "scope_kind"
    private const val KEY_FOLDERS = "scope_folders"
    private const val WHOLE_DEVICE = "WHOLE_DEVICE"
    private const val MEDIA_ONLY = "MEDIA_ONLY"
    private const val FOLDERS = "FOLDERS"

    fun toData(scope: ScanScope): Data = workDataOf(
        KEY_KIND to kindOf(scope),
        KEY_FOLDERS to (scope as? ScanScope.Folders)?.folders?.map { it.uri }?.toTypedArray(),
    )

    /** A folder whose grant was released since the scan was queued is dropped; null for unreadable input. */
    fun fromData(data: Data, pickedFolders: List<ScanFolder>): ScanScope? = when (data.getString(KEY_KIND)) {
        WHOLE_DEVICE -> ScanScope.WholeDevice
        MEDIA_ONLY -> ScanScope.MediaOnly
        FOLDERS -> {
            val uris = data.getStringArray(KEY_FOLDERS).orEmpty().toSet()
            ScanScope.Folders(pickedFolders.filter { it.uri in uris }.toImmutableList())
        }
        else -> null
    }

    /** A readable, stable name for `scan_run.scope`. */
    fun key(scope: ScanScope): String = when (scope) {
        is ScanScope.Folders -> "$FOLDERS:" + scope.folders.map { it.uri }.sorted().joinToString("|")
        else -> kindOf(scope)
    }

    /** The scope kind behind a [key]; null for a key this build doesn't know. */
    fun kindOf(key: String): ScopeKind? = when (key.substringBefore(':')) {
        WHOLE_DEVICE -> ScopeKind.WHOLE_DEVICE
        MEDIA_ONLY -> ScopeKind.MEDIA_ONLY
        FOLDERS -> ScopeKind.FOLDERS
        else -> null
    }

    private fun kindOf(scope: ScanScope) = when (scope) {
        ScanScope.WholeDevice -> WHOLE_DEVICE
        ScanScope.MediaOnly -> MEDIA_ONLY
        is ScanScope.Folders -> FOLDERS
    }
}
