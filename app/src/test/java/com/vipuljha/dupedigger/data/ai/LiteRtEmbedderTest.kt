package com.vipuljha.dupedigger.data.ai

import android.content.ComponentCallbacks2
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LiteRtEmbedderTest {
    private val text = ModelCatalog.variants.first()
    private val vision = ModelCatalog.variants[1]
    private val installed = mutableSetOf(text.id, vision.id)

    private val downloads = object : ModelDownloads {
        override fun start(variant: ModelVariant, wifiOnly: Boolean) = Unit
        override fun restart(variant: ModelVariant, wifiOnly: Boolean) = Unit
            override fun cancel(variant: ModelVariant) = Unit
        override suspend fun delete(variant: ModelVariant) = Unit
        override fun observe(variant: ModelVariant) = emptyFlow<ModelDownloadState>()
        override fun installedPath(variant: ModelVariant) = "/models/${variant.fileName}".takeIf { variant.id in installed }
    }

    /** Records loads and closes; GPU fails when [gpuWorks] is false; [failEmbed] makes the model throw. */
    private inner class FakeLoader : ModelLoader {
        val loads = mutableListOf<Pair<String, EmbeddingBackend>>()
        var closes = 0
        var gpuWorks = true
        var failEmbed = false
        val active = AtomicInteger()
        var maxActive = 0
        var sleepMs = 0L

        override fun load(path: String, variant: ModelVariant, backend: EmbeddingBackend): LoadedModel {
            loads += variant.id to backend
            if (backend == EmbeddingBackend.GPU && !gpuWorks) error("no GPU delegate")
            return object : LoadedModel {
                override fun embed(input: EmbeddingInput, dim: Int): FloatArray {
                    val now = active.incrementAndGet()
                    synchronized(this@FakeLoader) { maxActive = maxOf(maxActive, now) }
                    if (sleepMs > 0) Thread.sleep(sleepMs)
                    active.decrementAndGet()
                    if (failEmbed) error("native failure")
                    return FloatArray(dim) { 1f }
                }

                override fun close() {
                    closes++
                }
            }
        }
    }

    private val loader = FakeLoader()

    private fun TestScope.embedder() = LiteRtEmbedder(downloads, loader, StandardTestDispatcher(testScheduler), backgroundScope)

    private val inputs = listOf(EmbeddingInput.Text("a"), EmbeddingInput.Text("b"))

    @Test
    fun `loads on first use, returns one vector per input of the asked size, and reuses the engine`() = runTest {
        val embedder = embedder()
        assertTrue(loader.loads.isEmpty())

        val vectors = (embedder.embed(text, inputs, 256, EmbeddingBackend.CPU) as Outcome.Success).data
        embedder.embed(text, inputs, 256, EmbeddingBackend.CPU)

        assertEquals(listOf(256, 256), vectors.map { it.size })
        assertEquals(listOf(text.id to EmbeddingBackend.CPU), loader.loads)
    }

    @Test
    fun `a model that isn't downloaded is reported, nothing is loaded`() = runTest {
        installed.clear()
        assertEquals(Outcome.Failure(DomainError.ModelUnavailable), embedder().embed(text, inputs, 256, EmbeddingBackend.CPU))
        assertTrue(loader.loads.isEmpty())
    }

    @Test
    fun `a GPU that can't start falls back to CPU once and stays there`() = runTest {
        loader.gpuWorks = false
        val embedder = embedder()

        assertTrue(embedder.embed(text, inputs, 128, EmbeddingBackend.GPU) is Outcome.Success)
        embedder.embed(text, inputs, 128, EmbeddingBackend.GPU)

        assertEquals(listOf(text.id to EmbeddingBackend.GPU, text.id to EmbeddingBackend.CPU), loader.loads)
    }

    @Test
    fun `the engine closes after the idle time, and use within it keeps it open`() = runTest {
        val embedder = embedder()
        embedder.embed(text, inputs, 128, EmbeddingBackend.CPU)
        advanceTimeBy(LiteRtEmbedder.IDLE_CLOSE_MS - 1_000)
        embedder.embed(text, inputs, 128, EmbeddingBackend.CPU)
        advanceTimeBy(LiteRtEmbedder.IDLE_CLOSE_MS - 1_000)
        runCurrent()
        assertEquals(0, loader.closes)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, loader.closes)

        embedder.embed(text, inputs, 128, EmbeddingBackend.CPU)
        assertEquals(2, loader.loads.size)
    }

    @Test
    fun `memory trimmed in the background closes the engine, a hidden UI doesn't`() = runTest {
        val embedder = embedder()
        embedder.embed(text, inputs, 128, EmbeddingBackend.CPU)

        embedder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        runCurrent()
        assertEquals(0, loader.closes)
        embedder.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        runCurrent()
        assertEquals(1, loader.closes)
    }

    @Test
    fun `another model replaces the loaded one`() = runTest {
        val embedder = embedder()
        embedder.embed(text, inputs, 128, EmbeddingBackend.CPU)
        embedder.embed(vision, inputs, 128, EmbeddingBackend.CPU)

        assertEquals(1, loader.closes)
        assertEquals(listOf(text.id, vision.id), loader.loads.map { it.first })
    }

    @Test
    fun `an engine error is reported and the engine is dropped`() = runTest {
        loader.failEmbed = true
        val embedder = embedder()

        assertEquals(DomainError.Unknown, (embedder.embed(text, inputs, 128, EmbeddingBackend.CPU) as Outcome.Failure).error)
        assertEquals(1, loader.closes)
    }

    @Test
    fun `only one batch runs at a time`() = runTest {
        loader.sleepMs = 20
        val embedder = LiteRtEmbedder(downloads, loader, Dispatchers.Default, backgroundScope)

        List(4) { async(Dispatchers.Default) { embedder.embed(text, inputs, 64, EmbeddingBackend.CPU) } }.awaitAll()

        assertEquals(1, loader.maxActive)
    }
}
