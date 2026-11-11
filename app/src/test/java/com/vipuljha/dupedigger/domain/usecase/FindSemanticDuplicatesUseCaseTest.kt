package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.EmbeddedFile
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.model.NearGroup
import com.vipuljha.dupedigger.domain.model.VIDEO_EMBED_FRAMES
import com.vipuljha.dupedigger.domain.model.SIMILARITY_PROMPT
import com.vipuljha.dupedigger.domain.model.embeddingKey
import com.vipuljha.dupedigger.domain.repository.DocumentTextReader
import com.vipuljha.dupedigger.domain.repository.Embedder
import com.vipuljha.dupedigger.domain.repository.EmbeddingInputReader
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FindSemanticDuplicatesUseCaseTest {
    private val vision = ModelCatalog.variants.first { it.id == "embeddinggemma-440m-text-vision" }
    private val all = ModelCatalog.variants.first { it.id == "embeddinggemma-740m-all" }
    private val textOnly = ModelCatalog.variants.first { it.id == "embeddinggemma-270m-text" }

    /** Each image's bytes name its "scene"; images of one scene embed close together. */
    private fun vectorFor(scene: Int, jitter: Float) = FloatArray(4) { if (it == scene % 4) 1f else jitter }.let { v ->
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        FloatArray(4) { v[it] / n }
    }

    private val files = mutableListOf<EmbeddedFile>()
    private val stored = mutableMapOf<Pair<String, Long>, FloatArray>()
    private val index = object : FakeFileIndex(emptySet()) {
        override suspend fun embeddingCandidates(scanId: Long, modelKey: String, kinds: Set<MediaKind>) =
            files.filter { it.kind in kinds }
                .map { EmbeddedFile(it.id, it.sourceUri, it.name, it.mimeType, it.kind, it.size, stored[modelKey to it.id], it.seenNow) }
        override suspend fun saveEmbeddings(modelKey: String, vectors: Map<Long, FloatArray>) {
            vectors.forEach { (id, v) -> stored[modelKey to id] = v }
        }
    }

    private val embedded = mutableListOf<EmbeddingInput>()
    private var available = true
    private val embedder = object : Embedder {
        override suspend fun embed(variant: ModelVariant, inputs: List<EmbeddingInput>, dim: Int, backend: EmbeddingBackend): Outcome<List<FloatArray>> {
            if (!available) return Outcome.Failure(DomainError.ModelUnavailable)
            if (inputs.any { it is EmbeddingInput.Image && it.encoded[0] < 0 }) return Outcome.Failure(DomainError.Unknown)
            embedded += inputs
            return Outcome.Success(inputs.map { input ->
                when (input) {
                    // A real backend has been seen to hand back NaN for an image; scene NAN_SCENE stands in for that.
                    is EmbeddingInput.Image ->
                        if (input.encoded[0].toInt() == NAN_SCENE) FloatArray(4) { Float.NaN } else vectorFor(input.encoded[0].toInt(), input.encoded[1] / 100f)
                    is EmbeddingInput.Text -> vectorFor(input.text.length % 4, 0.05f)
                    is EmbeddingInput.Audio -> vectorFor(input.wav[0].toInt(), 0.05f)
                    // A video's frames read together into one vector, so the first frame's scene stands for it.
                    is EmbeddingInput.Video -> vectorFor(input.frames.first()[0].toInt(), input.frames.first()[1] / 100f)
                }
            })
        }
        override suspend fun close() = Unit
    }

    /** sourceUri → (scene, jitter in hundredths); scene -1 makes the engine reject the file. */
    private val images = mutableMapOf<String, Pair<Int, Int>>()

    /** sourceUri → one (scene, jitter) per keyframe. */
    private val videos = mutableMapOf<String, List<Pair<Int, Int>>>()
    private val texts = mutableMapOf<String, String>()
    private val find = FindSemanticDuplicatesUseCase(
        index,
        embedder,
        object : EmbeddingInputReader {
            override suspend fun image(sourceUri: String) = images[sourceUri]?.let { byteArrayOf(it.first.toByte(), it.second.toByte()) }
            override suspend fun audio(sourceUri: String): ByteArray? = null
            override suspend fun video(sourceUri: String) = videos[sourceUri]?.map { byteArrayOf(it.first.toByte(), it.second.toByte()) }
        },
        object : DocumentTextReader {
            override fun supports(name: String, mimeType: String?) = true
            override suspend fun text(sourceUri: String, name: String, mimeType: String?) = texts[sourceUri]
        },
    )

    private fun image(id: Long, scene: Int, jitter: Int = 5, seenNow: Boolean = true) {
        files += EmbeddedFile(id, "file:/i/$id.jpg", "$id.jpg", "image/jpeg", MediaKind.IMAGE, id * 10, null, seenNow)
        images["file:/i/$id.jpg"] = scene to jitter
    }

    private fun video(id: Long, scene: Int, jitter: Int = 5) {
        files += EmbeddedFile(id, "file:/v/$id.mp4", "$id.mp4", "video/mp4", MediaKind.VIDEO, id * 1000, null)
        videos["file:/v/$id.mp4"] = List(VIDEO_EMBED_FRAMES) { scene to jitter }
    }

    @Test
    fun `burst shots group, an unrelated image doesn't, and vectors are saved under the model key`() = runTest {
        image(1, scene = 1)
        image(2, scene = 1, jitter = 8)
        image(3, scene = 1, jitter = 3)
        image(4, scene = 2)

        val result = find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())!!

        assertEquals(listOf(listOf(1L, 2L, 3L)), result.groups.map { it.fileIds })
        assertTrue(result.groups.single().fingerprint.startsWith("${MatchTier.SEMANTIC}:"))
        assertEquals(setOf(1L, 2L, 3L, 4L), stored.keys.filter { it.first == embeddingKey(vision, EmbeddingBackend.CPU) }.map { it.second }.toSet())
    }

    @Test
    fun `stored vectors are reused, and another model's vectors are kept but not used`() = runTest {
        image(1, scene = 1)
        image(2, scene = 1)
        find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())
        val first = embedded.size

        find(2, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())
        assertEquals(first, embedded.size)

        find(3, all, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())
        assertEquals(first * 2, embedded.size)
        assertEquals(4, stored.size)
    }

    @Test
    fun `document text is prefixed with the similarity prompt and short documents are skipped`() = runTest {
        files += EmbeddedFile(10, "file:/d/a.txt", "a.txt", "text/plain", MediaKind.DOCUMENT, 1, null)
        files += EmbeddedFile(11, "file:/d/b.txt", "b.txt", "text/plain", MediaKind.DOCUMENT, 1, null)
        texts["file:/d/a.txt"] = (1..30).joinToString("  ") { "word$it" }
        texts["file:/d/b.txt"] = "too short"

        val result = find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())!!

        val text = embedded.filterIsInstance<EmbeddingInput.Text>().single().text
        assertTrue(text.startsWith(SIMILARITY_PROMPT + "word1 word2"))
        assertEquals(0, result.unreadable)
    }

    @Test
    fun `a group already shown as near duplicates isn't repeated`() = runTest {
        image(1, scene = 1)
        image(2, scene = 1)
        val near = NearGroup("NEAR:x", persistentListOf(1, 2), 0.98f)

        assertTrue(find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = listOf(near))!!.groups.isEmpty())
    }

    @Test
    fun `a file the engine rejects costs only itself`() = runTest {
        image(1, scene = 1)
        image(2, scene = 1)
        image(3, scene = -1)

        val result = find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())!!

        assertEquals(listOf(listOf(1L, 2L)), result.groups.map { it.fileIds })
        assertEquals(1, result.unreadable)
    }

    @Test
    fun `switching backend re-reads the files, because the two sets of vectors can't be compared`() = runTest {
        image(1, scene = 1)
        image(2, scene = 1)
        find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())
        val onCpu = embedded.size

        find(2, vision, EmbeddingBackend.GPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())

        assertEquals(onCpu * 2, embedded.size)
        assertEquals(setOf(EmbeddingBackend.CPU, EmbeddingBackend.GPU).map { embeddingKey(vision, it) }.toSet(), stored.keys.map { it.first }.toSet())
    }

    @Test
    fun `a vector that comes back unusable is never stored, and counts as unread`() = runTest {
        image(1, scene = 1)
        image(2, scene = 1)
        image(3, scene = NAN_SCENE)

        val result = find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())!!

        assertEquals(listOf(listOf(1L, 2L)), result.groups.map { it.fileIds })
        assertEquals(1, result.unreadable)
        assertTrue(stored.keys.none { it.second == 3L })
    }

    @Test
    fun `without the model the tier is skipped`() = runTest {
        image(1, scene = 1)
        available = false
        assertNull(find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList()))
    }

    @Test
    fun `videos of the same scene group, from their keyframes read together as one vector`() = runTest {
        video(1, scene = 3)
        video(2, scene = 3, jitter = 8)
        video(3, scene = 2)

        val result = find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())!!

        assertEquals(listOf(listOf(1L, 2L)), result.groups.map { it.fileIds })
        val clips = embedded.filterIsInstance<EmbeddingInput.Video>()
        assertEquals(3, clips.size)
        assertTrue(clips.all { it.frames.size == VIDEO_EMBED_FRAMES })
    }

    @Test
    fun `a text-only model leaves videos alone`() = runTest {
        video(1, scene = 3)
        video(2, scene = 3)

        val result = find(1, textOnly, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())!!

        assertTrue(result.groups.isEmpty())
        assertTrue(embedded.isEmpty())
    }

    @Test
    fun `a file scanned earlier matches on its stored vector, but is never embedded again`() = runTest {
        image(1, scene = 1)
        image(2, scene = 1)
        find(1, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())
        embedded.clear()
        image(3, scene = 1, seenNow = false)

        val result = find(2, vision, EmbeddingBackend.CPU, 0.9f, minSizeBytes = 0, exactGroups = emptyList(), nearGroups = emptyList())!!

        assertEquals(listOf(listOf(1L, 2L)), result.groups.map { it.fileIds })
        assertTrue(embedded.isEmpty())
    }

    private companion object {
        /** A scene the fake engine answers with NaN, the way a real backend sometimes does. */
        const val NAN_SCENE = 9
    }
}
