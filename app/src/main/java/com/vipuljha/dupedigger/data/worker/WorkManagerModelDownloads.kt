package com.vipuljha.dupedigger.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.data.ai.ModelStorage
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One model downloads at a time (one unique work name, KEEP), so two large files never compete for space.
 * Wi-Fi only maps to an unmetered network, which also allows Ethernet and excludes metered Wi-Fi; the choice is fixed
 * when the download is queued. [cancel] and [installedPath] touch the disk briefly; callers stay off the main thread.
 */
@Singleton
class WorkManagerModelDownloads @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val storage: ModelStorage,
) : ModelDownloads {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Bumped when a model file is deleted, which changes no work, so [observe] looks at the file again. */
    private val fileChanges = MutableStateFlow(0)

    override fun start(variant: ModelVariant, wifiOnly: Boolean) {
        workManager.enqueueUniqueWork(ModelDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, downloadRequest(variant, wifiOnly))
    }

    override fun restart(variant: ModelVariant, wifiOnly: Boolean) {
        workManager.enqueueUniqueWork(ModelDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, downloadRequest(variant, wifiOnly))
    }

    override fun cancel(variant: ModelVariant) {
        workManager.cancelAllWorkByTag(tagOf(variant))
        storage.fetcher.partFile(variant).delete()
    }

    override suspend fun delete(variant: ModelVariant) = withContext(ioDispatcher) {
        storage.fetcher.finalFile(variant).delete()
        fileChanges.update { it + 1 }
    }

    override fun observe(variant: ModelVariant): Flow<ModelDownloadState> =
        combine(workManager.getWorkInfosByTagFlow(tagOf(variant)), fileChanges) { infos, _ ->
            stateOf(infos, storage.fetcher.finalFile(variant).exists())
        }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override fun installedPath(variant: ModelVariant): String? = storage.fetcher.finalFile(variant).takeIf { it.exists() }?.path

    companion object {
        fun tagOf(variant: ModelVariant) = "model:${variant.id}"

        fun downloadRequest(variant: ModelVariant, wifiOnly: Boolean): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(ModelDownloadWorker.KEY_VARIANT to variant.id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(tagOf(variant))
                .build()

        /**
         * Unfinished work wins over finished work (a retry after a failure keeps the old entry); then the verified file
         * decides "downloaded" whatever the work says, so a later delete is seen.
         */
        fun stateOf(infos: List<WorkInfo>, installed: Boolean): ModelDownloadState {
            val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?: infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                ?: infos.lastOrNull()
            return stateOf(info, installed)
        }

        private fun stateOf(info: WorkInfo?, installed: Boolean): ModelDownloadState = when {
            info?.state == WorkInfo.State.RUNNING -> ModelDownloadState.Downloading(
                info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0),
                info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0),
            )
            info?.state == WorkInfo.State.ENQUEUED || info?.state == WorkInfo.State.BLOCKED -> ModelDownloadState.Queued
            installed -> ModelDownloadState.Downloaded
            info?.state == WorkInfo.State.FAILED ->
                ModelDownloadState.Failed(ModelDownloadWorker.failureOf(info.outputData.getString(ModelDownloadWorker.KEY_FAILURE)))
            else -> ModelDownloadState.NotDownloaded
        }
    }
}
