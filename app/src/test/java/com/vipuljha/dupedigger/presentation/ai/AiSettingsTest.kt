package com.vipuljha.dupedigger.presentation.ai

import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import com.vipuljha.dupedigger.domain.usecase.CancelModelDownloadUseCase
import com.vipuljha.dupedigger.domain.usecase.ChooseModelUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteModelUseCase
import com.vipuljha.dupedigger.domain.usecase.DownloadModelUseCase
import com.vipuljha.dupedigger.domain.usecase.SetAiBackendUseCase
import com.vipuljha.dupedigger.domain.usecase.SetAiChargingOnlyUseCase
import com.vipuljha.dupedigger.domain.usecase.SetModelWifiOnlyUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticThresholdUseCase
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import com.vipuljha.dupedigger.presentation.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** P6-05: the whole AI flow can be operated and undone through the AI settings screen. */
class AiSettingsTest {
    private val small = ModelCatalog.variants[0]
    private val medium = ModelCatalog.variants[1]

    private val settings = FakeSettings()
    private val states = ModelCatalog.variants.associate { it.id to MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded) }
    private val calls = mutableListOf<String>()
    private val downloads = object : ModelDownloads {
        override fun start(variant: ModelVariant, wifiOnly: Boolean) {
            calls += "start ${variant.id} $wifiOnly"
            states.getValue(variant.id).value = ModelDownloadState.Queued
        }
        override fun restart(variant: ModelVariant, wifiOnly: Boolean) {
            calls += "restart ${variant.id} $wifiOnly"
        }
        override fun cancel(variant: ModelVariant) {
            calls += "cancel ${variant.id}"
            states.getValue(variant.id).value = ModelDownloadState.NotDownloaded
        }
        override suspend fun delete(variant: ModelVariant) {
            states.getValue(variant.id).value = ModelDownloadState.NotDownloaded
        }
        override fun observe(variant: ModelVariant) = states.getValue(variant.id)
        override fun installedPath(variant: ModelVariant) = "/m/${variant.id}".takeIf { states.getValue(variant.id).value == ModelDownloadState.Downloaded }
    }
    private val deletedVectors = mutableListOf<String>()
    private val index = object : FakeFileIndex(emptySet()) {
        override suspend fun deleteEmbeddings(modelId: String) {
            deletedVectors += modelId
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AiViewModel(
        settings, downloads,
        DownloadModelUseCase(downloads, settings), CancelModelDownloadUseCase(downloads), ChooseModelUseCase(settings, downloads),
        DeleteModelUseCase(downloads, settings, index), SetSemanticEnabledUseCase(settings), SetAiBackendUseCase(settings),
        SetSemanticThresholdUseCase(settings), SetModelWifiOnlyUseCase(settings, downloads), SetAiChargingOnlyUseCase(settings),
    )

    private fun AiViewModel.ui() = (state.value.content as ContentState.Success).data

    @Test
    fun `download, choose, turn on, then delete undoes it all`() {
        val vm = viewModel()
        assertFalse(vm.ui().canEnable)

        vm.onIntent(AiIntent.DownloadClicked(medium.id))
        assertEquals("start ${medium.id} true", calls.last())
        assertTrue(vm.ui().models.first { it.id == small.id }.canDownload.not())
        assertTrue(vm.ui().models.first { it.id == medium.id }.status is ModelStatusUi.Queued)

        states.getValue(medium.id).value = ModelDownloadState.Downloaded
        vm.onIntent(AiIntent.UseClicked(medium.id))
        vm.onIntent(AiIntent.EnabledToggled(true))
        assertEquals(medium.id, settings.settings.value.aiModelId)
        assertTrue(vm.ui().enabled)
        assertTrue(vm.ui().models.first { it.id == medium.id }.chosen)

        vm.onIntent(AiIntent.DeleteClicked(medium.id))
        assertEquals(medium.id, vm.state.value.confirmDelete?.id)
        vm.onIntent(AiIntent.DeleteConfirmed)

        assertNull(settings.settings.value.aiModelId)
        assertFalse(settings.settings.value.semanticEnabled)
        assertEquals(listOf(medium.id), deletedVectors)
        assertTrue(vm.ui().models.first { it.id == medium.id }.status is ModelStatusUi.NotDownloaded)
    }

    @Test
    fun `the tier can't be turned on without a chosen model, and a model must be downloaded to be chosen`() {
        val vm = viewModel()
        vm.onIntent(AiIntent.EnabledToggled(true))
        vm.onIntent(AiIntent.UseClicked(small.id))

        assertFalse(settings.settings.value.semanticEnabled)
        assertNull(settings.settings.value.aiModelId)
    }

    @Test
    fun `changing wifi-only queues a waiting download again, and options are saved within range`() {
        val vm = viewModel()
        vm.onIntent(AiIntent.DownloadClicked(small.id))

        vm.onIntent(AiIntent.WifiOnlyToggled(false))
        vm.onIntent(AiIntent.ThresholdChanged(0.5f))
        vm.onIntent(AiIntent.BackendSelected(EmbeddingBackend.GPU))
        vm.onIntent(AiIntent.ChargingOnlyToggled(true))

        assertEquals("restart ${small.id} false", calls.last())
        val saved = settings.settings.value
        assertEquals(AppSettings.SemanticChoices.start, saved.semanticThreshold)
        assertEquals(EmbeddingBackend.GPU to true, saved.aiBackend to saved.aiOnlyWhileCharging)
    }

    @Test
    fun `cancel stops the download`() {
        val vm = viewModel()
        vm.onIntent(AiIntent.DownloadClicked(small.id))
        vm.onIntent(AiIntent.CancelClicked(small.id))

        assertEquals("cancel ${small.id}", calls.last())
        assertTrue(vm.ui().models.all { it.canDownload })
    }
}
