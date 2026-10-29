package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.data.hashing.StreamingContentHasher
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ExactScanResult
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.util.Collections
import kotlin.random.Random

/** The exact engine over real temp files: a fake index and the real SHA-256 hasher. */
class FindExactDuplicatesUseCaseTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val hasher = StreamingContentHasher({ uri -> opened += uri; FileInputStream(File(URI(uri))) }, Dispatchers.IO)
    private val index = FakeIndex()
    private val findExact = FindExactDuplicatesUseCase(index, hasher)

    private class FakeIndex : FileIndexRepository {
        val files = linkedMapOf<Long, IndexedFile>()

        override suspend fun record(files: List<DiscoveredFile>, scanId: Long) = IndexStats()
        override suspend fun removeNotSeen(scope: ScanScope, scanId: Long) = 0
        override suspend fun filesSeenIn(scanId: Long) = synchronized(this) { files.values.toList() }
        override suspend fun saveHashes(files: List<IndexedFile>) = synchronized(this) { files.forEach { this.files[it.id] = it } }
        override suspend fun filesByIds(ids: Collection<Long>) = emptyList<StoredFile>()
        override suspend fun remove(ids: Collection<Long>) = Unit
    }

    private fun add(name: String, bytes: ByteArray): Long {
        val f = File(tmp.root, name).apply { writeBytes(bytes) }
        val id = index.files.size + 1L
        index.files[id] = IndexedFile(id, f.toURI().toString(), f.length(), f.lastModified(), null, null)
        return id
    }

    private fun run(minSize: Long = 1): ExactScanResult = runBlocking { findExact(scanId = 1, minSizeBytes = minSize) }

    @Test
    fun `identical copies are grouped and a unique size is never opened`() {
        val photo = Random(1).nextBytes(200 * 1024)
        val a = add("a.jpg", photo)
        val b = add("copy of a.jpg", photo)
        val c = add("b/a.jpg", photo.also { File(tmp.root, "b").mkdirs() })
        add("other.jpg", Random(2).nextBytes(200 * 1024))
        val unique = add("unique.bin", Random(3).nextBytes(10))

        val result = run()

        assertEquals(1, result.groups.size)
        assertEquals(listOf(a, b, c), result.groups.single().fileIds)
        assertEquals(200L * 1024, result.groups.single().size)
        assertTrue(index.files.getValue(unique).sourceUri !in opened)
        assertEquals(4, result.partialHashed)
        assertEquals(3, result.fullHashed)
    }

    @Test
    fun `same size files that differ only in the middle need a full hash and stay apart`() {
        val base = Random(4).nextBytes(100 * 1024)
        add("x.bin", base)
        add("y.bin", base.copyOf().apply { this[50_000] = (this[50_000] + 1).toByte() })

        val result = run()

        assertTrue(result.groups.isEmpty())
        assertEquals(2, result.fullHashed)
    }

    @Test
    fun `empty files are skipped by default and grouped when the minimum size is 0`() {
        val e1 = add("e1", ByteArray(0))
        val e2 = add("e2", ByteArray(0))

        assertTrue(run(minSize = 1).groups.isEmpty())
        assertTrue(opened.isEmpty())

        val groups = run(minSize = 0).groups
        assertEquals(listOf(e1, e2), groups.single().fileIds)
        assertEquals(0L, groups.single().size)
    }

    @Test
    fun `small identical files are matched from the single partial read`() {
        val note = "same text".toByteArray()
        add("n1.txt", note)
        add("n2.txt", note)
        add("n3.txt", "diff text".toByteArray())

        val result = run()

        assertEquals(1, result.groups.size)
        assertEquals(3, result.partialHashed)
        assertEquals(0, result.fullHashed)
    }

    @Test
    fun `an unreadable file is skipped and the rest still group`() {
        val bytes = Random(5).nextBytes(64 * 1024)
        val a = add("a.bin", bytes)
        val b = add("b.bin", bytes)
        val gone = add("gone.bin", bytes)
        File(URI(index.files.getValue(gone).sourceUri)).delete()

        val result = run()

        assertEquals(listOf(a, b), result.groups.single().fileIds)
        assertEquals(1, result.unreadable)
    }

    @Test
    fun `a second run reuses cached hashes and opens nothing`() {
        val big = Random(6).nextBytes(300 * 1024)
        add("v1.mp4", big)
        add("v2.mp4", big)
        add("s1.txt", "abc".toByteArray())
        add("s2.txt", "abc".toByteArray())
        val first = run()
        opened.clear()

        val second = run()

        assertEquals(first.groups, second.groups)
        assertEquals(2, second.groups.size)
        assertEquals(0, second.partialHashed)
        assertEquals(0, second.fullHashed)
        assertTrue(opened.isEmpty())
    }
}
