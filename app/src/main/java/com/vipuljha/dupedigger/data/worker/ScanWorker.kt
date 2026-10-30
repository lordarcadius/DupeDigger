package com.vipuljha.dupedigger.data.worker

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.common.runSuspendCatching
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.usecase.RunScanUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Runs one scan in the background.
 * - Asks to run in the foreground with a progress notification; started from the background (a restart after
 *   process death) that isn't allowed on API 31+, so it then runs as a normal job, and per-file caching keeps its progress.
 * - Progress goes to WorkManager and the notification at most every [REPORT_INTERVAL_MS] per stage.
 */
@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runScan: RunScanUseCase,
    private val scanFolders: ScanFolderRepository,
    private val notifications: ScanNotifications,
) : CoroutineWorker(context, params) {

    private val reportLock = Mutex()
    private var inForeground = false
    private var lastReported: ScanProgress? = null
    private var lastReportedAt = 0L

    override suspend fun doWork(): Result {
        val scope = ScanScopeCodec.fromData(inputData, scanFolders.folders.first()) ?: return Result.failure()
        inForeground = runSuspendCatching { setForeground(notifications.foregroundInfo(id, null)) }
            .onFailure { Timber.w(it, "Scanning without a foreground notification") }
            .isSuccess
        return when (val outcome = runScan(scope, ::report)) {
            is Outcome.Success -> Result.success()
            is Outcome.Failure -> {
                Timber.w(outcome.cause, "Scan failed: %s", outcome.error)
                Result.failure()
            }
        }
    }

    private suspend fun report(progress: ScanProgress) = reportLock.withLock {
        val last = lastReported
        val now = SystemClock.elapsedRealtime()
        if (last != null && last.stage == progress.stage && (now - lastReportedAt < REPORT_INTERVAL_MS || progress.done < last.done)) {
            return@withLock
        }
        lastReported = progress
        lastReportedAt = now
        setProgress(progress.toData())
        if (inForeground) {
            runSuspendCatching { setForeground(notifications.foregroundInfo(id, progress)) }
                .onFailure { Timber.w(it, "Couldn't update the scan notification") }
        }
    }

    companion object {
        const val UNIQUE_NAME = "scan"
        private const val REPORT_INTERVAL_MS = 500L
        private const val KEY_STAGE = "stage"
        private const val KEY_DONE = "done"
        private const val KEY_TOTAL = "total"

        fun ScanProgress.toData(): Data = workDataOf(KEY_STAGE to stage.name, KEY_DONE to done, KEY_TOTAL to (total ?: -1))

        /** Progress is empty until the first report, so a running scan without it reads as just started. */
        fun Data.toScanProgress(): ScanProgress {
            val stage = getString(KEY_STAGE)?.let(ScanStage::valueOf) ?: return ScanProgress(ScanStage.INDEXING, 0, null)
            return ScanProgress(stage, getInt(KEY_DONE, 0), getInt(KEY_TOTAL, -1).takeIf { it >= 0 })
        }
    }
}
