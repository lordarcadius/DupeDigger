package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import com.vipuljha.dupedigger.presentation.FakeSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiUseCasesTest {
    private val settings = FakeSettings()
    private val small = ModelCatalog.variants.first()
    private val large = ModelCatalog.variants.last()

    private val downloads = object : ModelDownloads {
        val installed = mutableSetOf<String>()
        val states = mutableMapOf<String, ModelDownloadState>()
        val calls = mutableListOf<String>()

        override fun start(variant: ModelVariant, wifiOnly: Boolean) {
            calls += "start ${variant.id} $wifiOnly"
        }
        override fun restart(variant: ModelVariant, wifiOnly: Boolean) {
            calls += "restart ${variant.id} $wifiOnly"
        }
        override fun cancel(variant: ModelVariant) {
            calls += "cancel ${variant.id}"
        }
        override suspend fun delete(variant: ModelVariant) {
            calls += "delete ${variant.id}"
            installed -= variant.id
        }
        override fun observe(variant: ModelVariant) = flowOf(states[variant.id] ?: ModelDownloadState.NotDownloaded)
        override fun installedPath(variant: ModelVariant) = if (variant.id in installed) "/models/${variant.id}" else null
    }

    private val deletedVectors = mutableListOf<String>()
    private val fileIndex = object : FakeFileIndex(emptySet()) {
        override suspend fun deleteEmbeddings(modelId: String) {
            deletedVectors += modelId
        }
    }

    @Test
    fun `only a downloaded model can be chosen`() = runTest {
        assertFalse(ChooseModelUseCase(settings, downloads)(small))
        assertNull(settings.settings.value.aiModelId)

        downloads.installed += small.id
        assertTrue(ChooseModelUseCase(settings, downloads)(small))
        assertEquals(small.id, settings.settings.value.aiModelId)
    }

    @Test
    fun `the AI tier turns on only with a chosen model`() = runTest {
        val enable = SetSemanticEnabledUseCase(settings)
        enable(true)
        assertFalse(settings.settings.value.semanticEnabled)

        settings.update { it.copy(aiModelId = small.id) }
        enable(true)
        assertTrue(settings.settings.value.semanticEnabled)
        enable(false)
        assertFalse(settings.settings.value.semanticEnabled)
    }

    @Test
    fun `backend, charging rule and threshold are saved, the threshold within its range`() = runTest {
        SetAiBackendUseCase(settings)(EmbeddingBackend.GPU)
        SetAiChargingOnlyUseCase(settings)(true)
        SetSemanticThresholdUseCase(settings)(0.5f)
        assertEquals(EmbeddingBackend.GPU, settings.settings.value.aiBackend)
        assertTrue(settings.settings.value.aiOnlyWhileCharging)
        assertEquals(AppSettings.SemanticChoices.start, settings.settings.value.semanticThreshold)

        SetSemanticThresholdUseCase(settings)(0.93f)
        assertEquals(0.93f, settings.settings.value.semanticThreshold)
    }

    @Test
    fun `changing Wi-Fi only queues waiting and running downloads again, and nothing else`() = runTest {
        downloads.states[small.id] = ModelDownloadState.Queued
        downloads.states[large.id] = ModelDownloadState.Downloaded

        SetModelWifiOnlyUseCase(settings, downloads)(false)

        assertFalse(settings.settings.value.modelDownloadWifiOnly)
        assertEquals(listOf("restart ${small.id} false"), downloads.calls)
    }

    @Test
    fun `cancel stops the download`() {
        CancelModelDownloadUseCase(downloads)(small)
        assertEquals(listOf("cancel ${small.id}"), downloads.calls)
    }

    @Test
    fun `deleting the chosen model removes its file and vectors and turns the tier off`() = runTest {
        downloads.installed += small.id
        settings.update { it.copy(aiModelId = small.id, semanticEnabled = true) }

        DeleteModelUseCase(downloads, settings, fileIndex)(small)

        assertEquals(listOf("cancel ${small.id}", "delete ${small.id}"), downloads.calls)
        assertEquals(listOf(small.id), deletedVectors)
        assertNull(settings.settings.value.aiModelId)
        assertFalse(settings.settings.value.semanticEnabled)
    }

    @Test
    fun `deleting another model leaves the chosen one in use`() = runTest {
        settings.update { it.copy(aiModelId = small.id, semanticEnabled = true) }

        DeleteModelUseCase(downloads, settings, fileIndex)(large)

        assertEquals(small.id, settings.settings.value.aiModelId)
        assertTrue(settings.settings.value.semanticEnabled)
        assertEquals(listOf(large.id), deletedVectors)
    }
}
