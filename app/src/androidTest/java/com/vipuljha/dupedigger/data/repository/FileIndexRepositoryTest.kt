package com.vipuljha.dupedigger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.entity.EmbeddingEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.local.db.entity.PerceptualHashEntity
import com.vipuljha.dupedigger.data.storage.FileTreeWalker
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.usecase.IndexFilesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import java.io.IOException

/** P2-04: the incremental indexer over a real Room database and a real folder walk. */
@RunWith(AndroidJUnit4::class)
class FileIndexRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "indexer-test")
    private val walker = FileTreeWalker { null }
    private val filter = ScanFilter(emptyList(), skipHidden = false)
    private lateinit var db: DupeDiggerDatabase
    private lateinit var index: IndexFilesUseCase
    private var failAfter = Int.MAX_VALUE

    private val enumerator = object : FileEnumerator {
        override fun enumerate(scope: ScanScope, filter: ScanFilter): Flow<DiscoveredFile> {
            var emitted = 0
            return walker.walk(listOf(root), filter).onEach { if (++emitted > failAfter) throw IOException("walk failed") }
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        val repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao())
        index = IndexFilesUseCase(enumerator, repository)
        root.deleteRecursively()
        repeat(FOLDERS) { d ->
            val dir = File(root, "d$d").apply { mkdirs() }
            repeat(FILES_PER_FOLDER) { f -> File(dir, "f$f.bin").writeBytes(byteArrayOf(d.toByte(), f.toByte())) }
        }
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    /** What the exact stage would have to hash: indexed files without a cached hash. */
    private fun filesNeedingHash(): Int = db.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM file_index f LEFT JOIN file_hash h ON h.fileId = f.id WHERE h.fileId IS NULL",
    ).use { it.moveToFirst(); it.getInt(0) }

    private suspend fun hashEverything() {
        val ids = db.openHelper.readableDatabase.query("SELECT id, mtime FROM file_index").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getLong(1)) }
        }
        ids.forEach { (id, mtime) -> db.fileHashDao().upsert(FileHashEntity(id, "p$id", "f$id", mtime)) }
    }

    private suspend fun idOf(file: File) = db.fileIndexDao().findBySourceUri(file.toURI().toString())?.id

    @Test
    fun unchangedRescanRecomputesNothingAndChangesInvalidateOnlyThatFile() = runBlocking {
        val total = FOLDERS * FILES_PER_FOLDER
        val mediaRow = FileIndexEntity(
            sourceUri = "content://media/external_primary/file/1", path = null, name = "m.jpg", mimeType = "image/jpeg",
            mediaKind = "IMAGE", size = 1, mtime = 1, volume = "external_primary", lastSeenScanId = 1,
        )
        val mediaId = db.fileIndexDao().insert(mediaRow)
        db.fileHashDao().upsert(FileHashEntity(mediaId, "p", "f", 1))

        assertEquals(IndexStats(added = total), index(ScanScope.WholeDevice, filter, scanId = 1))
        assertEquals(total, filesNeedingHash())
        hashEverything()
        val edited = File(root, "d3/f7.bin")
        val editedId = idOf(edited)!!
        db.perceptualHashDao().upsertAll(listOf(PerceptualHashEntity(editedId, "DHASH", 0, 42)))
        db.embeddingDao().upsert(EmbeddingEntity(editedId, "m", 2, ByteArray(8)))

        val started = System.nanoTime()
        val rescan = index(ScanScope.WholeDevice, filter, scanId = 2)
        Timber.i("Unchanged rescan of %d files took %d ms", total, (System.nanoTime() - started) / 1_000_000)
        assertEquals(IndexStats(unchanged = total), rescan)
        assertEquals(0, filesNeedingHash())
        assertEquals(editedId, idOf(edited))

        edited.appendBytes(byteArrayOf(9))
        edited.setLastModified(edited.lastModified() + 5_000)
        val removed = File(root, "d0/f0.bin").apply { assertTrue(delete()) }
        assertEquals(IndexStats(changed = 1, unchanged = total - 2, removed = 1), index(ScanScope.WholeDevice, filter, scanId = 3))
        assertEquals(1, filesNeedingHash())
        assertEquals(editedId, idOf(edited))
        assertNull(db.fileHashDao().get(editedId))
        assertTrue(db.perceptualHashDao().byAlgo("DHASH").isEmpty())
        assertTrue(db.embeddingDao().forModel("m").isEmpty())
        assertNull(idOf(removed))

        // Rows from another scope are never pruned by a whole-device scan.
        assertNotNull(db.fileIndexDao().findBySourceUri(mediaRow.sourceUri))
        assertNotNull(db.fileHashDao().get(mediaId))
    }

    @Test
    fun aFailedScanPrunesNothing() = runBlocking {
        val total = FOLDERS * FILES_PER_FOLDER
        index(ScanScope.WholeDevice, filter, scanId = 1)
        File(root, "d0/f0.bin").delete()

        failAfter = 10
        val error = runCatching { index(ScanScope.WholeDevice, filter, scanId = 2) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(total, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM file_index").use { it.moveToFirst(); it.getInt(0) })
    }

    private companion object {
        // 2,200 files: five batches, the last one partial.
        const val FOLDERS = 20
        const val FILES_PER_FOLDER = 110
    }
}
