package com.vipuljha.dupedigger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** P5-01: image hashes are cached per file, read back for the scan that saw it, and dropped when the file changes. */
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
}
