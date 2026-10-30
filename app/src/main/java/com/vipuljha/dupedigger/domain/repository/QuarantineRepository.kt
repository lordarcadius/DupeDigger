package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.QuarantinedFile
import kotlinx.coroutines.flow.Flow

interface QuarantineRepository {
    /** Newest first. */
    fun observeAll(): Flow<List<QuarantinedFile>>

    /** Puts the file back where it was; fails without changing anything if something now sits there. */
    suspend fun restore(id: Long): Outcome<Unit>

    suspend fun deletePermanently(id: Long): Outcome<Unit>
}
