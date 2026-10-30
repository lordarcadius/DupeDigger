package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import kotlinx.coroutines.flow.Flow

/** Runs scans in the background so they survive leaving the app and process death; one scan at a time. */
interface ScanScheduler {
    /** Does nothing while a scan is already queued or running. */
    fun start(scope: ScanScope)

    fun cancel()

    /** The running scan's latest progress, or null when none is running. */
    fun observeProgress(): Flow<ScanProgress?>
}
