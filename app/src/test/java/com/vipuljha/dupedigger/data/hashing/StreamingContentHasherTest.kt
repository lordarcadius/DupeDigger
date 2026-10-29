package com.vipuljha.dupedigger.data.hashing

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.common.getOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import kotlin.random.Random

class StreamingContentHasherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val hasher = StreamingContentHasher({ uri -> FileInputStream(File(URI(uri))) }, Dispatchers.IO)

    private fun file(name: String, bytes: ByteArray) = File(tmp.root, name).apply { writeBytes(bytes) }

    private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun partial(f: File) = runBlocking { hasher.partialHash(f.toURI().toString(), f.length()).getOrNull()!! }

    private fun full(f: File) = runBlocking { hasher.fullHash(f.toURI().toString(), f.length()).getOrNull()!! }

    @Test
    fun `full hash is the SHA-256 of the content across several buffers`() {
        val bytes = Random(1).nextBytes(700 * 1024 + 3)
        assertEquals(sha256Hex(bytes), full(file("big.bin", bytes)))
    }

    @Test
    fun `partial hash sees the size, head and tail but not the middle`() {
        val base = Random(2).nextBytes(100 * 1024)
        val middle = base.copyOf().apply { this[50 * 1024] = (this[50 * 1024] + 1).toByte() }
        val tail = base.copyOf().apply { this[lastIndex] = (this[lastIndex] + 1).toByte() }
        val a = file("a.bin", base)

        assertEquals(partial(a), partial(file("middle.bin", middle)))
        assertNotEquals(partial(a), partial(file("tail.bin", tail)))
        assertNotEquals(partial(a), partial(file("longer.bin", base + 0)))
        assertNull(partial(a).full)
    }

    @Test
    fun `a small file is read once and gets its full hash with the partial`() {
        val bytes = Random(3).nextBytes(32 * 1024)
        val hashes = partial(file("small.bin", bytes))
        assertEquals(sha256Hex(bytes), hashes.full)
        assertNotEquals(hashes.partial, hashes.full)
        assertEquals(sha256Hex(ByteArray(0)), partial(file("empty.bin", ByteArray(0))).full)
    }

    @Test
    fun `a name with spaces and a hash sign round-trips through its URI`() {
        val bytes = "hello".toByteArray()
        assertEquals(sha256Hex(bytes), full(file("my #1 file.txt", bytes)))
    }

    @Test
    fun `a missing file or a size change fails instead of hashing`() = runBlocking {
        val gone = File(tmp.root, "gone.bin").toURI().toString()
        assertEquals(DomainError.NotFound, (hasher.partialHash(gone, 10) as Outcome.Failure).error)

        val f = file("grew.bin", ByteArray(100))
        assertTrue(hasher.partialHash(f.toURI().toString(), 99) is Outcome.Failure)
        assertTrue(hasher.fullHash(f.toURI().toString(), 101) is Outcome.Failure)
        val big = file("shrank.bin", ByteArray(64 * 1024))
        assertTrue(hasher.partialHash(big.toURI().toString(), 64 * 1024 + 1) is Outcome.Failure)
    }
}
