package com.vipuljha.dupedigger.data.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.detector.cosine
import com.vipuljha.dupedigger.domain.detector.groupBySimilarity
import com.vipuljha.dupedigger.domain.model.EMBEDDING_DIM
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
import java.io.File

/**
 * P6-04 golden set: burst shots of one scene group at the default 0.90 threshold and an unrelated photo doesn't, with
 * the real 440M model. Downloads 388 MB, so it only runs when asked: `-e aiModel true`.
 */
@RunWith(AndroidJUnit4::class)
class SemanticGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storage = ModelStorage(context, Dispatchers.IO)
    private val variant = ModelCatalog.variants.first { it.id == "embeddinggemma-440m-text-vision" }
    private val dir = File(context.cacheDir, "semantic-golden").apply { mkdirs() }
    private val downloads = object : ModelDownloads {
        override fun start(variant: ModelVariant, wifiOnly: Boolean) = Unit
        override fun cancel(variant: ModelVariant) = Unit
        override suspend fun delete(variant: ModelVariant) = Unit
        override fun observe(variant: ModelVariant) = emptyFlow<ModelDownloadState>()
        override fun installedPath(variant: ModelVariant) = storage.fetcher.finalFile(variant).takeIf { it.exists() }?.path
    }

    @Before
    fun onlyWhenAsked() = assumeTrue(InstrumentationRegistry.getArguments().getString("aiModel") == "true")

    /** A beach-like scene; [shift] moves the people and the ball a little, as between burst frames. */
    private fun beach(name: String, shift: Float): File {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(120, 190, 240))
        paint.color = Color.rgb(230, 210, 150)
        canvas.drawRect(0f, 750f, 1600f, 1200f, paint)
        paint.color = Color.rgb(40, 110, 190)
        canvas.drawRect(0f, 600f, 1600f, 760f, paint)
        paint.color = Color.rgb(255, 230, 80)
        canvas.drawCircle(1300f, 200f, 110f, paint)
        paint.color = Color.rgb(200, 60, 50)
        canvas.drawRect(500f + shift, 640f, 580f + shift, 900f, paint)
        canvas.drawRect(760f + shift * 1.4f, 660f, 830f + shift * 1.4f, 910f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(1000f + shift * 3, 820f - shift, 45f, paint)
        return save(bitmap, name)
    }

    /** An indoor desk: a different subject, colours and layout. */
    private fun desk(name: String): File {
        val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(90, 70, 55))
        paint.color = Color.rgb(40, 40, 45)
        canvas.drawRect(400f, 250f, 1200f, 750f, paint)
        paint.color = Color.rgb(110, 160, 220)
        canvas.drawRect(440f, 290f, 1160f, 710f, paint)
        paint.color = Color.rgb(230, 230, 230)
        canvas.drawRect(450f, 850f, 1150f, 1000f, paint)
        paint.color = Color.rgb(250, 250, 240)
        canvas.drawCircle(1350f, 900f, 90f, paint)
        return save(bitmap, name)
    }

    private fun save(bitmap: Bitmap, name: String) = File(dir, name).apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } }

    @Test
    fun burstShotsGroupAndAnUnrelatedPhotoDoesNot() = runBlocking<Unit> {
        assertTrue(storage.fetcher.fetch(variant) is FetchResult.Done)
        val reader = AndroidEmbeddingInputReader(context, Dispatchers.IO)
        val embedder = LiteRtEmbedder(downloads, LiteRtModelLoader(context), Dispatchers.IO, CoroutineScope(SupervisorJob()))
        val photos = listOf(beach("burst1.jpg", 0f), beach("burst2.jpg", 25f), beach("burst3.jpg", 55f), desk("desk.jpg"))
        val inputs = photos.map { EmbeddingInput.Image(reader.image(it.toURI().toString())!!) }

        val vectors = (embedder.embed(variant, inputs, EMBEDDING_DIM, EmbeddingBackend.CPU) as Outcome.Success).data
        val cosines = vectors.indices.flatMap { i -> (0 until i).map { j -> "$j-$i %.3f".format(cosine(vectors[i], vectors[j])) } }
        Log.i(TAG, "cosines: $cosines")

        assertEquals(listOf(listOf(0L, 1L, 2L)), groupBySimilarity(vectors.mapIndexed { i, v -> i.toLong() to v }, 0.90f))
        embedder.close()
        dir.deleteRecursively()
        storage.fetcher.finalFile(variant).delete()
    }

    private companion object {
        const val TAG = "SemanticGolden"
    }
}
