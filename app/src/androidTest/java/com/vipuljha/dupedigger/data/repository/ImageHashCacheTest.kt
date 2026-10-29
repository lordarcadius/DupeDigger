package com.vipuljha.dupedigger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.entity.PerceptualHashEntity
import com.vipuljha.dupedigger.domain.model.AudioSignature
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.DocumentFile
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.VIDEO_FRAMES
import com.vipuljha.dupedigger.domain.model.VideoFile
import com.vipuljha.dupedigger.domain.model.VideoSignature
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** P5-01 to P5-05: image hashes, video signatures, document SimHashes and audio signatures are cached per file, read back for the scan that saw it, and dropped when the file changes. */
@RunWith(AndroidJUnit4::class)
class ImageHashCacheTest {
    private lateinit var db: DupeDiggerDatabase
    private lateinit var repository: FileIndexRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DupeDiggerDatabase::class.java).build()
        repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao())
    }

    @After
    fun tearDown() = db.close()

    private fun file(name: String, kind: MediaKind, mtime: Long = 1) =
        DiscoveredFile("file:/p/$name", "/p/$name", name, null, kind, size = 10, mtime = mtime, volume = null)

    @Test
    fun hashesReadBackUntilTheFileChanges() = runBlocking<Unit> {
        repository.record(listOf(file("a.jpg", MediaKind.IMAGE), file("b.jpg", MediaKind.IMAGE), file("c.pdf", MediaKind.DOCUMENT)), scanId = 1)
        val (a, b) = repository.imagesSeenIn(1).sortedBy { it.sourceUri }
        assertEquals(listOf(null, null), listOf(a.hashes, b.hashes))

        repository.saveImageHashes(mapOf(a.id to ImageHashes(1, 2), b.id to ImageHashes(3, 4)))
        repository.record(listOf(file("a.jpg", MediaKind.IMAGE), file("b.jpg", MediaKind.IMAGE, mtime = 2)), scanId = 2)

        assertEquals(
            listOf(ImageFile(a.id, "file:/p/a.jpg", 10, ImageHashes(1, 2)), ImageFile(b.id, "file:/p/b.jpg", 10, null)),
            repository.imagesSeenIn(2).sortedBy { it.sourceUri },
        )
    }

    @Test
    fun videoSignaturesReadBackOnlyWhenComplete() = runBlocking<Unit> {
        repository.record(listOf(file("a.mp4", MediaKind.VIDEO), file("b.mp4", MediaKind.VIDEO), file("c.jpg", MediaKind.IMAGE)), scanId = 1)
        val (a, b) = repository.videosSeenIn(1).sortedBy { it.sourceUri }
        val signature = VideoSignature(60_000, List(VIDEO_FRAMES) { it * 3L }.toImmutableList())

        repository.saveVideoSignatures(mapOf(a.id to signature))
        db.perceptualHashDao().upsertAll(listOf(PerceptualHashEntity(b.id, "VIDEO_DURATION", 0, 5_000)))

        assertEquals(
            listOf(VideoFile(a.id, "file:/p/a.mp4", 10, signature), VideoFile(b.id, "file:/p/b.mp4", 10, null)),
            repository.videosSeenIn(1).sortedBy { it.sourceUri },
        )
        repository.record(listOf(file("a.mp4", MediaKind.VIDEO, mtime = 2)), scanId = 2)
        assertEquals(listOf(VideoFile(a.id, "file:/p/a.mp4", 10, null)), repository.videosSeenIn(2))
    }

    @Test
    fun documentAndAudioSignaturesReadBack() = runBlocking<Unit> {
        repository.record(
            listOf(file("a.pdf", MediaKind.DOCUMENT), file("b.mp3", MediaKind.AUDIO), file("c.m4a", MediaKind.AUDIO)),
            scanId = 1,
        )
        val doc = repository.documentsSeenIn(1).single()
        val (b, c) = repository.audioSeenIn(1).sortedBy { it.name }
        assertEquals(listOf(null, null, null), listOf(doc.simHash, b.signature, c.signature))

        repository.saveDocumentHashes(mapOf(doc.id to 42L))
        repository.saveAudioSignatures(mapOf(b.id to AudioSignature(200_000, 7, 9), c.id to AudioSignature(200_500, null, null)))

        assertEquals(DocumentFile(doc.id, "file:/p/a.pdf", "a.pdf", null, 10, 42L), repository.documentsSeenIn(1).single())
        assertEquals(
            listOf(AudioSignature(200_000, 7, 9), AudioSignature(200_500, null, null)),
            repository.audioSeenIn(1).sortedBy { it.name }.map { it.signature },
        )
    }
}
