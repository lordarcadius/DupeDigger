package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/** Absolute folder paths that are never scanned. */
interface ExcludedPathRepository {
    val paths: Flow<ImmutableList<String>>

    /** Excludes the folder behind a SAF tree; fails with NotFound when it isn't on local storage. */
    suspend fun excludeTree(treeUri: String): Outcome<Unit>

    suspend fun remove(path: String)
}
