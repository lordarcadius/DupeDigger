package com.vipuljha.dupedigger.data.ai

import com.sun.net.httpserver.HttpServer
import com.vipuljha.dupedigger.domain.model.ModelCapability
import com.vipuljha.dupedigger.domain.model.ModelDownloadFailure
import com.vipuljha.dupedigger.domain.model.ModelVariant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.random.Random

/** Downloads from a local HTTP server that can cut a transfer short, ignore Range, or serve the wrong bytes. */
class ModelFetcherTest {
    @get:Rule val temp = TemporaryFolder()

    private val body = Random(1).nextBytes(3 * 1024 * 1024 + 123)
    private var served = body
    private var cutAfter: Int? = null
    private var honourRange = true
    private val ranges = mutableListOf<String?>()
    private lateinit var server: HttpServer

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/model") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            ranges += range
            val start = range?.takeIf { honourRange }?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            val bytes = served.copyOfRange(start, served.size)
            if (start > 0) exchange.responseHeaders.add("Content-Range", "bytes $start-${served.size - 1}/${served.size}")
            exchange.sendResponseHeaders(if (start > 0) 206 else 200, bytes.size.toLong())
            exchange.responseBody.use { out ->
                val limit = cutAfter
                if (limit != null) {
                    cutAfter = null
                    out.write(bytes, 0, limit)
                    out.flush()
                    exchange.close()
                } else {
                    out.write(bytes)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun variant(sha: String = sha256(body)) = ModelVariant(
        id = "test", fileName = "model.litertlm", url = "http://127.0.0.1:${server.address.port}/model",
        sizeBytes = body.size.toLong(), sha256 = sha, capabilities = setOf(ModelCapability.TEXT), peakRamMb = null,
    )

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fetcher(free: Long = Long.MAX_VALUE) = ModelFetcher(temp.root, freeBytes = { free })

    @Test
    fun `an interrupted download resumes with a Range request and ends verified`() = runTest {
        cutAfter = 1_000_000
        val fetcher = fetcher()

        val first = fetcher.fetch(variant())
        assertEquals(FetchResult.Failed(ModelDownloadFailure.NETWORK, retryable = true), first)
        val kept = fetcher.partFile(variant()).length()
        assertTrue("kept $kept", kept in 1..body.size - 1)

        val second = fetcher.fetch(variant())

        assertEquals("bytes=$kept-", ranges.last())
        val file = (second as FetchResult.Done).file
        assertArrayEquals(body, file.readBytes())
        assertFalse(fetcher.partFile(variant()).exists())
    }

    @Test
    fun `a corrupt download is rejected and its partial file deleted`() = runTest {
        served = body.copyOf().also { it[42] = (it[42] + 1).toByte() }
        val fetcher = fetcher()

        assertEquals(FetchResult.Failed(ModelDownloadFailure.CHECKSUM, retryable = false), fetcher.fetch(variant()))
        assertFalse(fetcher.partFile(variant()).exists())
        assertFalse(fetcher.finalFile(variant()).exists())
    }

    @Test
    fun `a server that ignores Range restarts the file instead of appending`() = runTest {
        cutAfter = 500_000
        honourRange = false
        val fetcher = fetcher()
        fetcher.fetch(variant())

        val result = fetcher.fetch(variant())

        assertArrayEquals(body, (result as FetchResult.Done).file.readBytes())
    }

    @Test
    fun `too little free space fails before downloading`() = runTest {
        val result = fetcher(free = body.size.toLong()).fetch(variant())

        assertEquals(FetchResult.Failed(ModelDownloadFailure.NO_SPACE, retryable = false), result)
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `a whole partial file is verified without another request`() = runTest {
        val fetcher = fetcher()
        temp.root.mkdirs()
        fetcher.partFile(variant()).writeBytes(body)

        assertTrue(fetcher.fetch(variant()) is FetchResult.Done)
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `an installed model isn't downloaded again`() = runTest {
        val fetcher = fetcher()
        temp.root.mkdirs()
        fetcher.finalFile(variant()).writeBytes(body)

        assertTrue(fetcher.fetch(variant()) is FetchResult.Done)
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `a client error is final`() = runTest {
        val missing = variant().copy(url = "http://127.0.0.1:${server.address.port}/missing")
        assertEquals(FetchResult.Failed(ModelDownloadFailure.HTTP, retryable = false), fetcher().fetch(missing))
    }
}
