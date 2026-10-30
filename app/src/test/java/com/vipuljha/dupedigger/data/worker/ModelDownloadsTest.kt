package com.vipuljha.dupedigger.data.worker

import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadFailure
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import com.vipuljha.dupedigger.domain.usecase.DownloadModelUseCase
import com.vipuljha.dupedigger.presentation.FakeSettings
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ModelDownloadsTest {
    private val variant = ModelCatalog.variants.first()

    @Test
    fun `the catalog pins each D-006 file to a revision and checksum`() {
        assertEquals(3, ModelCatalog.variants.map { it.id }.toSet().size)
        assertTrue(ModelCatalog.variants.all { it.url.contains("/resolve/") && it.url.endsWith(it.fileName) && it.sha256.length == 64 })
        assertEquals(variant, ModelCatalog.byId(variant.id))
    }

    @Test
    fun `wifi only waits for an unmetered network, otherwise any connection`() {
        assertEquals(NetworkType.UNMETERED, WorkManagerModelDownloads.downloadRequest(variant, wifiOnly = true).workSpec.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, WorkManagerModelDownloads.downloadRequest(variant, wifiOnly = false).workSpec.constraints.requiredNetworkType)
        assertTrue(WorkManagerModelDownloads.tagOf(variant) in WorkManagerModelDownloads.downloadRequest(variant, true).tags)
    }

    private fun info(state: WorkInfo.State, output: Pair<String, String>? = null, progress: Pair<Long, Long>? = null) = WorkInfo(
        UUID.randomUUID(), state, emptySet(),
        outputData = output?.let { workDataOf(it) } ?: workDataOf(),
        progress = progress?.let { workDataOf(ModelDownloadWorker.KEY_DOWNLOADED to it.first, ModelDownloadWorker.KEY_TOTAL to it.second) } ?: workDataOf(),
    )

    @Test
    fun `state follows the work, and a verified file means downloaded`() {
        assertEquals(ModelDownloadState.Downloading(10, 100), WorkManagerModelDownloads.stateOf(listOf(info(WorkInfo.State.RUNNING, progress = 10L to 100L)), false))
        assertEquals(ModelDownloadState.Queued, WorkManagerModelDownloads.stateOf(listOf(info(WorkInfo.State.ENQUEUED)), false))
        assertEquals(
            ModelDownloadState.Failed(ModelDownloadFailure.CHECKSUM),
            WorkManagerModelDownloads.stateOf(listOf(info(WorkInfo.State.FAILED, ModelDownloadWorker.KEY_FAILURE to "CHECKSUM")), false),
        )
        assertEquals(ModelDownloadState.Downloaded, WorkManagerModelDownloads.stateOf(listOf(info(WorkInfo.State.SUCCEEDED)), true))
        assertEquals(ModelDownloadState.NotDownloaded, WorkManagerModelDownloads.stateOf(listOf(info(WorkInfo.State.SUCCEEDED)), false))
        assertEquals(ModelDownloadState.NotDownloaded, WorkManagerModelDownloads.stateOf(emptyList(), false))
    }

    @Test
    fun `a running retry wins over the failed attempt before it`() {
        val failed = info(WorkInfo.State.FAILED, ModelDownloadWorker.KEY_FAILURE to "NETWORK")
        val running = info(WorkInfo.State.RUNNING, progress = 5L to 100L)
        assertEquals(ModelDownloadState.Downloading(5, 100), WorkManagerModelDownloads.stateOf(listOf(running, failed), false))
        assertEquals(ModelDownloadState.Downloading(5, 100), WorkManagerModelDownloads.stateOf(listOf(failed, running), false))
    }

    @Test
    fun `a download takes the wifi-only setting at the time it is queued`() = runTest {
        val started = mutableListOf<Pair<ModelVariant, Boolean>>()
        val downloads = object : ModelDownloads {
            override fun start(variant: ModelVariant, wifiOnly: Boolean) {
                started += variant to wifiOnly
            }
            override fun restart(variant: ModelVariant, wifiOnly: Boolean) = Unit
            override fun cancel(variant: ModelVariant) = Unit
            override suspend fun delete(variant: ModelVariant) = Unit
            override fun observe(variant: ModelVariant) = emptyFlow<ModelDownloadState>()
            override fun installedPath(variant: ModelVariant): String? = null
        }
        val settings = FakeSettings()
        val download = DownloadModelUseCase(downloads, settings)

        download(variant)
        settings.update { it.copy(modelDownloadWifiOnly = false) }
        download(variant)

        assertEquals(listOf(variant to true, variant to false), started)
    }
}
