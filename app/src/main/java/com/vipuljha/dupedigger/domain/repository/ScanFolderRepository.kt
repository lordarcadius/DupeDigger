package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.ScanFolder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/** Folders picked through SAF; the grant is persisted, so they survive restarts. */
interface ScanFolderRepository {
    val folders: Flow<ImmutableList<ScanFolder>>

    suspend fun add(treeUri: String): Outcome<Unit>

    suspend fun remove(treeUri: String)
}
