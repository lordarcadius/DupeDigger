package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.nearFingerprint
import com.vipuljha.dupedigger.domain.model.VIDEO_FRAMES
import com.vipuljha.dupedigger.domain.model.VideoFile
import com.vipuljha.dupedigger.domain.model.VideoSignature
import com.vipuljha.dupedigger.domain.repository.ImageHasher
import com.vipuljha.dupedigger.domain.repository.VideoHasher
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindNearDuplicatesUseCaseTest {
    private val close = ImageHashes(0b1L, 0b11L)
    private val origin = ImageHashes(0L, 0L)

    private val index = object : FakeFileIndex(emptySet()) {
        val images = mutableListOf<ImageFile>()
        val saved = mutableMapOf<Long, ImageHashes>()
        override suspend fun imagesSeenIn(scanId: Long) = images.map { it.copy(hashes = it.hashes ?: saved[it.id]) }
        override suspend fun saveImageHashes(hashes: Map<Long, ImageHashes>) {
            saved += hashes
        }
        val videos = mutableListOf<VideoFile>()
        val savedVideos = mutableMapOf<Long, VideoSignature>()
        override suspend fun videosSeenIn(scanId: Long) = videos.map { it.copy(signature = it.signature ?: savedVideos[it.id]) }
        override suspend fun saveVideoSignatures(signatures: Map<Long, VideoSignature>) {
            savedVideos += signatures
        }
    }

    /** Decodes only the URIs it knows. */
    private val decoded = mutableMapOf<String, ImageHashes>()
    private val requested = mutableListOf<String>()
    private val signed = mutableMapOf<String, VideoSignature>()
    private val find = FindNearDuplicatesUseCase(
        index,
        object : ImageHasher {
            override suspend fun hash(sourceUri: String): ImageHashes? {
                requested += sourceUri
                return decoded[sourceUri]
            }
        },
        object : VideoHasher {
            override suspend fun signature(sourceUri: String): VideoSignature? {
                requested += sourceUri
                return signed[sourceUri]
            }
        },
    )

    private fun video(id: Long, signature: VideoSignature? = null) = VideoFile(id, "file:/v/$id.mp4", size = id * 100, signature = signature)

    private fun clip(durationMs: Long, flip: Int = 0) = VideoSignature(durationMs, List(VIDEO_FRAMES) { it.toLong() * 0x0101_0101_0101L xor ((1L shl flip) - 1) }.toImmutableList())

    @Test
    fun `similar videos group beside images, one progress total covering both`() = runTest {
        index.images += listOf(image(1, origin), image(2))
        decoded["file:/p/2.jpg"] = close
        index.videos += listOf(video(10, clip(60_000)), video(11), video(12))
        signed["file:/v/11.mp4"] = clip(60_400, flip = 3)
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = find(scanId = 1, threshold = 10, exactGroups = emptyList()) { done, total -> progress += done to total }

        assertEquals(listOf(listOf(1L, 2L), listOf(10L, 11L)), result.groups.map { it.fileIds })
        assertEquals(mapOf(11L to clip(60_400, flip = 3)), index.savedVideos)
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(1, result.unreadable)
        assertEquals(1100L, result.sizes[11L])
        assertEquals(1 - 3f / 64, result.groups.last().score, 1e-6f)
    }

    private fun image(id: Long, hashes: ImageHashes? = null) = ImageFile(id, "file:/p/$id.jpg", size = id * 10, hashes = hashes)

    @Test
    fun `similar images group, with cached hashes reused and new ones saved`() = runTest {
        index.images += listOf(image(1, origin), image(2))
        decoded["file:/p/2.jpg"] = close

        val result = find(scanId = 1, threshold = 10, exactGroups = emptyList())

        assertEquals(listOf("file:/p/2.jpg"), requested)
        assertEquals(mapOf(2L to close), index.saved)
        val group = result.groups.single()
        assertEquals(listOf(1L, 2L), group.fileIds)
        assertEquals(nearFingerprint(listOf("file:/p/2.jpg", "file:/p/1.jpg")), group.fingerprint)
        assertEquals(1 - 2f / 64, group.score, 1e-6f)
        assertEquals(mapOf(1L to 10L, 2L to 20L), result.sizes)
    }

    @Test
    fun `an exact group takes part through one member only`() = runTest {
        index.images += listOf(image(1, origin), image(2, origin), image(3, close))

        val result = find(scanId = 1, threshold = 10, exactGroups = listOf(ExactGroup(10, "h", persistentListOf(2, 1))))

        assertEquals(listOf(listOf(1L, 3L)), result.groups.map { it.fileIds })
    }

    @Test
    fun `an image that can't be decoded is skipped and counted`() = runTest {
        index.images += listOf(image(1, origin), image(2))

        val result = find(scanId = 1, threshold = 10, exactGroups = emptyList())

        assertTrue(result.groups.isEmpty())
        assertEquals(1, result.unreadable)
        assertEquals(0, result.hashed)
    }

    @Test
    fun `the near fingerprint follows the members, in any order`() {
        assertEquals(nearFingerprint(listOf("a", "b")), nearFingerprint(listOf("b", "a")))
        assertTrue(nearFingerprint(listOf("a", "b")) != nearFingerprint(listOf("a", "c")))
        assertTrue(nearFingerprint(listOf("a")).startsWith("NEAR:"))
    }
}
