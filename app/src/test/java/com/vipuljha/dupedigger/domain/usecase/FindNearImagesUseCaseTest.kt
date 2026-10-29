package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.nearFingerprint
import com.vipuljha.dupedigger.domain.repository.ImageHasher
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindNearImagesUseCaseTest {
    private val close = ImageHashes(0b1L, 0b11L)
    private val origin = ImageHashes(0L, 0L)

    private val index = object : FakeFileIndex(emptySet()) {
        val images = mutableListOf<ImageFile>()
        val saved = mutableMapOf<Long, ImageHashes>()
        override suspend fun imagesSeenIn(scanId: Long) = images.map { it.copy(hashes = it.hashes ?: saved[it.id]) }
        override suspend fun saveImageHashes(hashes: Map<Long, ImageHashes>) {
            saved += hashes
        }
    }

    /** Decodes only the URIs it knows. */
    private val decoded = mutableMapOf<String, ImageHashes>()
    private val requested = mutableListOf<String>()
    private val find = FindNearImagesUseCase(
        index,
        object : ImageHasher {
            override suspend fun hash(sourceUri: String): ImageHashes? {
                requested += sourceUri
                return decoded[sourceUri]
            }
        },
    )

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
