package com.vipuljha.dupedigger.data.worker

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vipuljha.dupedigger.core.common.runSuspendCatching
import com.vipuljha.dupedigger.data.ai.FetchResult
import com.vipuljha.dupedigger.data.ai.ModelStorage
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Downloads one model in the foreground with a progress notification.
 * - Network trouble retries with WorkManager's backoff and resumes from the partial file, up to [MAX_ATTEMPTS] runs.
 * - A corrupt file, too little space or a refused request fails for good, with the reason in the output data.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val storage: ModelStorage,
    private val notifications: ModelDownloadNotifications,
) : CoroutineWorker(context, params) {

    private var lastReportedAt = 0L

    override suspend fun doWork(): Result {
        val variant = inputData.getString(KEY_VARIANT)?.let(ModelCatalog::byId) ?: return Result.failure()
        val start = storage.fetcher.partFile(variant).length()
        Timber.i("Model download %s starting at byte %d (attempt %d)", variant.id, start, runAttemptCount + 1)
        runSuspendCatching { setForeground(notifications.foregroundInfo(id, start, variant.sizeBytes)) }
            .onFailure { Timber.w(it, "Downloading the model without a foreground notification") }
        val result = storage.fetcher.fetch(variant) { downloaded, total ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastReportedAt >= REPORT_INTERVAL_MS || downloaded == total) {
                lastReportedAt = now
                setProgress(workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total))
                runSuspendCatching { setForeground(notifications.foregroundInfo(id, downloaded, total)) }
            }
        }
        return when (result) {
            is FetchResult.Done -> Result.success()
            is FetchResult.Failed -> when {
                result.retryable && runAttemptCount + 1 < MAX_ATTEMPTS -> Result.retry()
                else -> Result.failure(workDataOf(KEY_FAILURE to result.reason.name))
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "model-download"
        const val KEY_VARIANT = "variant"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_FAILURE = "failure"
        private const val MAX_ATTEMPTS = 6
        private const val REPORT_INTERVAL_MS = 500L

        fun failureOf(name: String?): ModelDownloadFailure =
            ModelDownloadFailure.entries.firstOrNull { it.name == name } ?: ModelDownloadFailure.NETWORK
    }
}
