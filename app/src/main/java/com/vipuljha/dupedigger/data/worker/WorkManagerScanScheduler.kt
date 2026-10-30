package com.vipuljha.dupedigger.data.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vipuljha.dupedigger.data.worker.ScanWorker.Companion.toScanProgress
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerScanScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ScanScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun start(scope: ScanScope) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(ScanScopeCodec.toData(scope))
            .build()
        workManager.enqueueUniqueWork(ScanWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(ScanWorker.UNIQUE_NAME)
    }

    override fun observeProgress(): Flow<ScanProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow(ScanWorker.UNIQUE_NAME)
            .map { infos -> infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.toScanProgress() }
            .distinctUntilChanged()
}
