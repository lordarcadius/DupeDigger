package com.vipuljha.dupedigger.data.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * P6-02: the real LiteRT-LM engine embeds text and an image at the asked size. Downloads the 440M text + vision model
 * (388 MB, checked by SHA-256), so it only runs when asked: `am instrument -w -e aiModel true -e class <this class> <runner>`.
 */
@RunWith(AndroidJUnit4::class)
class EmbedderDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storage = ModelStorage(context, Dispatchers.IO)
    private val variant = ModelCatalog.variants.first { it.id == "embeddinggemma-440m-text-vision" }
    private val downloads = object : ModelDownloads {
        override fun start(variant: ModelVariant, wifiOnly: Boolean) = Unit
        override fun restart(variant: ModelVariant, wifiOnly: Boolean) = Unit
        override fun cancel(variant: ModelVariant) = Unit
        override suspend fun delete(variant: ModelVariant) = Unit
        override fun observe(variant: ModelVariant) = emptyFlow<ModelDownloadState>()
        override fun installedPath(variant: ModelVariant) = storage.fetcher.finalFile(variant).takeIf { it.exists() }?.path
    }

    @Before
    fun onlyWhenAsked() = assumeTrue(InstrumentationRegistry.getArguments().getString("aiModel") == "true")

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(40, 110, 200))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW }
        canvas.drawCircle(380f, 100f, 60f, paint)
        paint.color = Color.rgb(30, 140, 60)
        canvas.drawRect(0f, 280f, 512f, 384f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun norm(v: FloatArray) = sqrt(v.sumOf { (it * it).toDouble() })
    private fun cosine(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }

    @Test
    fun embedsTextAndAnImageAtTheAskedSize() = runBlocking<Unit> {
        val download = storage.fetcher.fetch(variant)
        assertTrue("download: $download", download is FetchResult.Done)
        val embedder = LiteRtEmbedder(downloads, LiteRtModelLoader(context), Dispatchers.IO, CoroutineScope(SupervisorJob()))
        val prefix = "task: sentence similarity | query: "
        val inputs = listOf(
            EmbeddingInput.Text(prefix + "a cat sleeping on a sofa"),
            EmbeddingInput.Text(prefix + "a kitten asleep on a couch"),
            EmbeddingInput.Text(prefix + "quarterly tax report for the finance team"),
            EmbeddingInput.Image(jpeg()),
        )

        for (dim in listOf(256, 768)) {
            val start = System.nanoTime()
            val vectors = (embedder.embed(variant, inputs, dim, EmbeddingBackend.CPU) as Outcome.Success).data
            Log.i(TAG, "CPU dim $dim: ${(System.nanoTime() - start) / 1_000_000} ms; norms ${vectors.map { "%.4f".format(norm(it)) }}; " +
                "cat~kitten ${"%.3f".format(cosine(vectors[0], vectors[1]))}, cat~tax ${"%.3f".format(cosine(vectors[0], vectors[2]))}")
            assertEquals(List(4) { dim }, vectors.map { it.size })
            assertTrue(vectors.all { kotlin.math.abs(norm(it) - 1) < 1e-3 })
            assertTrue(cosine(vectors[0], vectors[1]) > cosine(vectors[0], vectors[2]))
        }

        // The emulator's host GPU process can crash on a GPU init, so that path runs only on request (-e gpu true).
        if (InstrumentationRegistry.getArguments().getString("gpu") == "true") {
            val start = System.nanoTime()
            val gpu = embedder.embed(variant, inputs.take(1), 256, EmbeddingBackend.GPU)
            Log.i(TAG, "GPU request: ${gpu::class.simpleName} in ${(System.nanoTime() - start) / 1_000_000} ms")
            assertTrue(gpu is Outcome.Success)
        }
        embedder.close()
        storage.fetcher.finalFile(variant).delete()
    }

    private companion object {
        const val TAG = "EmbedderDevice"
    }
}
