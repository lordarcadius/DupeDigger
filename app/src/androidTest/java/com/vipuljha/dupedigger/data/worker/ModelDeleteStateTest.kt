package com.vipuljha.dupedigger.data.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.ai.ModelStorage
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** P6-05: deleting a downloaded model shows it as not downloaded again, though no download work changed. */
@RunWith(AndroidJUnit4::class)
class ModelDeleteStateTest {
    @Test
    fun deletingTheFileUpdatesTheState() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = ModelStorage(context, Dispatchers.IO)
        val downloads = WorkManagerModelDownloads(context, Dispatchers.IO, storage)
        val variant = ModelCatalog.variants.first()
        storage.fetcher.finalFile(variant).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(16)) }

        val seen = MutableStateFlow<ModelDownloadState?>(null)
        val watcher = launch(Dispatchers.Default) { downloads.observe(variant).collect { seen.value = it } }
        withTimeout(5_000) { seen.first { it == ModelDownloadState.Downloaded } }

        downloads.delete(variant)

        assertEquals(ModelDownloadState.NotDownloaded, withTimeout(5_000) { seen.first { it == ModelDownloadState.NotDownloaded } })
        watcher.cancel()
    }
}
