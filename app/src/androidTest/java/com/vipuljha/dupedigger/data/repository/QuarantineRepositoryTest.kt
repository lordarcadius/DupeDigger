package com.vipuljha.dupedigger.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.entity.QuarantineItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** P4-05: restore puts a quarantined file back only where nothing sits now; delete permanently erases it. */
@RunWith(AndroidJUnit4::class)
class QuarantineRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "quarantine-test")
    private lateinit var db: DupeDiggerDatabase
    private lateinit var repository: QuarantineRepositoryImpl

    @Before
    fun setUp() {
        root.deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        repository = QuarantineRepositoryImpl(context, Dispatchers.IO, db.quarantineDao())
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    /** A file already moved into quarantine from [original], which no longer exists. */
    private suspend fun quarantined(original: File, text: String): Long {
        val copy = File(root, "q/${original.name}").apply { parentFile!!.mkdirs(); writeText(text) }
        return db.quarantineDao().insert(
            QuarantineItemEntity(
                sourceUri = original.toURI().toString(), originalPath = original.path, quarantinePath = copy.path,
                size = copy.length(), movedAt = 1,
            ),
        )
    }

    @Test
    fun restorePutsTheFileBackAndForgetsIt() = runBlocking<Unit> {
        val original = File(root, "docs/notes.txt")
        val id = quarantined(original, "hello")

        assertEquals(listOf("notes.txt"), repository.observeAll().first().map { it.name })
        assertEquals(Outcome.Success(Unit), repository.restore(id))

        assertEquals("hello", original.readText())
        assertFalse(File(root, "q/notes.txt").exists())
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun restoreNeverOverwritesAFileNowInThatPlace() = runBlocking<Unit> {
        val original = File(root, "docs/notes.txt")
        val id = quarantined(original, "hello")
        original.parentFile!!.mkdirs()
        original.writeText("newer")

        assertEquals(Outcome.Failure(DomainError.Io), repository.restore(id))

        assertEquals("newer", original.readText())
        assertTrue(File(root, "q/notes.txt").exists())
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun deletePermanentlyErasesTheCopyAndTheRecord() = runBlocking<Unit> {
        val id = quarantined(File(root, "docs/notes.txt"), "hello")

        assertEquals(Outcome.Success(Unit), repository.deletePermanently(id))

        assertFalse(File(root, "q/notes.txt").exists())
        assertTrue(repository.observeAll().first().isEmpty())
        assertEquals(Outcome.Failure(DomainError.NotFound), repository.restore(id))
    }
}
