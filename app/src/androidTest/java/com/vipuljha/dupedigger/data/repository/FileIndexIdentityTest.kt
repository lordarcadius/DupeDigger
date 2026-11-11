package com.vipuljha.dupedigger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.storage.StorageVolumes
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** P10-01: a renamed or moved file is the same row, so it keeps what it already cost to hash. */
@RunWith(AndroidJUnit4::class)
class FileIndexIdentityTest {
    private lateinit var db: DupeDiggerDatabase
    private lateinit var repository: FileIndexRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao(), StorageVolumes(context))
    }

    @After
    fun tearDown() = db.close()

    private fun file(path: String, key: String?, size: Long = 10, mtime: Long = 1) = DiscoveredFile(
        sourceUri = "file:$path",
        path = path,
        name = path.substringAfterLast('/'),
        mimeType = null,
        mediaKind = MediaKind.IMAGE,
        size = size,
        mtime = mtime,
        volume = null,
        fileKey = key,
    )

    @Test
    fun aMovedFileKeepsItsRowAndItsHashes() = runBlocking<Unit> {
        repository.record(listOf(file("/dcim/a.jpg", "(dev=1,ino=7)")), scanId = 1)
        val before = repository.indexedImages(1).single()
        repository.saveImageHashes(mapOf(before.id to ImageHashes(1, 2)))

        repository.record(listOf(file("/pictures/holiday.jpg", "(dev=1,ino=7)")), scanId = 2)

        val after = repository.indexedImages(2).single()
        assertEquals(before.id, after.id)
        assertEquals("file:/pictures/holiday.jpg", after.sourceUri)
        assertEquals(ImageHashes(1, 2), after.hashes)
    }

    @Test
    fun aFileKeyThatCameBackOnDifferentContentIsNotTrusted() = runBlocking<Unit> {
        repository.record(listOf(file("/dcim/a.jpg", "(dev=1,ino=7)")), scanId = 1)
        repository.saveImageHashes(mapOf(repository.indexedImages(1).single().id to ImageHashes(1, 2)))

        repository.record(listOf(file("/dcim/b.jpg", "(dev=1,ino=7)", size = 99)), scanId = 2)

        val fresh = repository.indexedImages(2).single { it.sourceUri == "file:/dcim/b.jpg" }
        assertNull(fresh.hashes)
    }

    @Test
    fun hardLinksSharingAFileKeyGetARowEach() = runBlocking<Unit> {
        repository.record(listOf(file("/dcim/a.jpg", "(dev=1,ino=7)"), file("/pictures/a.jpg", "(dev=1,ino=7)")), scanId = 1)

        val rows = repository.indexedImages(1).sortedBy { it.sourceUri }
        assertEquals(listOf("file:/dcim/a.jpg", "file:/pictures/a.jpg"), rows.map { it.sourceUri })
        assertNotEquals(rows[0].id, rows[1].id)
    }

    @Test
    fun withoutAFileKeyAMoveIsANewRow() = runBlocking<Unit> {
        repository.record(listOf(file("/dcim/a.jpg", key = null)), scanId = 1)
        val before = repository.indexedImages(1).single()

        repository.record(listOf(file("/pictures/a.jpg", key = null)), scanId = 2)

        val rows = repository.indexedImages(2)
        assertEquals(2, rows.size)
        assertEquals(listOf(false, true), rows.sortedBy { it.sourceUri }.map { it.seenNow })
        assertEquals(before.id, rows.single { it.sourceUri == "file:/dcim/a.jpg" }.id)
    }
}
