package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.embeddingKey
import com.vipuljha.dupedigger.domain.model.socKey
import com.vipuljha.dupedigger.presentation.FakeDownloads
import com.vipuljha.dupedigger.presentation.FakeSoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P11-01: which build of the chosen model a scan actually runs, and on which backend. */
class ResolveEmbeddingModelUseCaseTest {
    private val model = ModelCatalog.variants.first { it.id == "embeddinggemma-440m-text-vision" }
    private val downloads = FakeDownloads()
    private val soc = FakeSoc()
    private val resolve = ResolveEmbeddingModelUseCase(downloads, soc)

    @Test
    fun `cpu and gpu always run the portable file`() {
        soc.soc = "SM8650"
        downloads.installed += resolve.npuBuild(model)!!.id

        for (backend in listOf(EmbeddingBackend.CPU, EmbeddingBackend.GPU)) {
            assertEquals(model to backend, resolve(model, backend).let { it.variant to it.backend })
        }
    }

    @Test
    fun `the npu build runs only once it is downloaded`() {
        soc.soc = "SM8650"

        val withoutIt = resolve(model, EmbeddingBackend.NPU)
        assertEquals(model, withoutIt.variant)
        assertEquals(EmbeddingBackend.GPU, withoutIt.backend)

        downloads.installed += resolve.npuBuild(model)!!.id
        val withIt = resolve(model, EmbeddingBackend.NPU)
        assertEquals("SM8650", withIt.variant.socTarget)
        assertEquals(EmbeddingBackend.NPU, withIt.backend)
    }

    @Test
    fun `a chip with no build falls back, whatever the backend asks for`() {
        soc.soc = "SM7325"

        assertNull(resolve.npuBuild(model))
        assertEquals(EmbeddingBackend.GPU, resolve(model, EmbeddingBackend.NPU).backend)
    }

    @Test
    fun `the chip name is matched without case or punctuation`() {
        assertEquals(socKey("Tensor G5"), socKey("tensor_g5"))
        soc.soc = "Tensor G5"

        assertEquals("Tensor_G5", resolve.npuBuild(model)?.socTarget)
    }

    @Test
    fun `an npu build keeps the model's capabilities but has its own file and vector key`() {
        soc.soc = "SM8750"
        val build = resolve.npuBuild(model)!!

        assertEquals(model.capabilities, build.capabilities)
        assertEquals(model.id, build.baseId)
        assertNotEquals(model.sha256, build.sha256)
        assertNotEquals(embeddingKey(model), embeddingKey(build))
        assertTrue(build.url.endsWith("embeddinggemma-2-text-vision-440m_Qualcomm_SM8750.litertlm"))
    }

    @Test
    fun `every catalogued model has a build for every chip the app targets`() {
        for (chip in ModelCatalog.npuSocKeys) {
            soc.soc = chip
            for (variant in ModelCatalog.variants) {
                val build = resolve.npuBuild(variant)
                assertEquals("$chip is missing a build of ${variant.id}", chip, build?.socTarget?.let(::socKey))
                assertTrue("${build?.id} has no size", build!!.sizeBytes > 0)
                assertEquals("${build.id} has a malformed digest", 64, build.sha256.length)
            }
        }
    }
}
