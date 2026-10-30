package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import kotlinx.coroutines.flow.Flow

interface ScanRunRepository {
    /** Records a new running scan and returns its id; any run still marked running is closed as interrupted. */
    suspend fun start(scope: ScanScope): Long

    suspend fun finish(scanId: Long, status: ScanStatus, summary: ScanSummary?)

    fun observeLatest(): Flow<ScanRunInfo?>
}
