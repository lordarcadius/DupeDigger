package com.vipuljha.dupedigger.data.worker

import android.Manifest
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

/** P3-02 on a device: the Hilt worker runs a MediaOnly scan through the real scheduler and records the run. */
@RunWith(AndroidJUnit4::class)
class ScanWorkerDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun aQueuedScanRunsToCompletionAndRecordsItsRun() = runBlocking {
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissions.forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
        val scheduler = WorkManagerScanScheduler(context)
        val seen = mutableListOf<ScanProgress?>()
        val watcher = launch { scheduler.observeProgress().collect { seen += it } }

        scheduler.start(ScanScope.MediaOnly)
        val finished = withTimeout(120_000) {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ScanWorker.UNIQUE_NAME)
                .first { infos -> infos.isNotEmpty() && infos.all { it.state.isFinished } }
                .single()
        }
        watcher.cancel()
        Timber.i("Scan finished as %s; progress seen: %s", finished.state, seen)

        assertEquals(WorkInfo.State.SUCCEEDED, finished.state)
        val db = Room.databaseBuilder(context, DupeDiggerDatabase::class.java, DupeDiggerDatabase.NAME).build()
        try {
            val run = db.scanRunDao().observeLatest().first()!!
            Timber.i("Latest run: %s", run)
            assertEquals(ScanStatus.COMPLETED.name, run.status)
            assertEquals("MEDIA_ONLY", run.scope)
        } finally {
            db.close()
        }
    }
}
