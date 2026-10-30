package com.vipuljha.dupedigger.data.worker

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.ai.ModelStorage
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * P6-01 "kill and restart mid-download resumes it", run as two separate instrumentation runs against the real
 * Hugging Face file (270M, 164 MB): [startThenGetKilled] queues the download and returns once part of it has arrived,
 * and the instrumentation then kills the app process; [resumesAfterRestart] runs in a new process.
 * Skipped unless asked for, since it downloads 164 MB and needs the two runs in order:
 * `am instrument -w -e modelDownload true -e class <this class>#startThenGetKilled <runner>`, then the same with
 * `#resumesAfterRestart`. [rangeSurvivesTheRedirect] checks the CDN still honours Range after the redirect.
 */
@RunWith(AndroidJUnit4::class)
class ModelDownloadResumeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storage = ModelStorage(context, Dispatchers.IO)
    private val downloads = WorkManagerModelDownloads(context, Dispatchers.IO, storage)
    private val variant = ModelCatalog.variants.first { it.id == "embeddinggemma-270m-text" }

    @Before
    fun onlyWhenAsked() = assumeTrue(InstrumentationRegistry.getArguments().getString("modelDownload") == "true")

    @Test
    fun rangeSurvivesTheRedirect() {
        val connection = URL(variant.url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", "bytes=1000-1999")
        try {
            Log.i(TAG, "range probe: HTTP ${connection.responseCode}, ${connection.getHeaderField("Content-Range")}")
            assertEquals(206, connection.responseCode)
            assertEquals("bytes 1000-1999/${variant.sizeBytes}", connection.getHeaderField("Content-Range"))
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun startThenGetKilled() {
        storage.fetcher.finalFile(variant).delete()
        downloads.start(variant, wifiOnly = false)
        val part = storage.fetcher.partFile(variant)
        val deadline = System.currentTimeMillis() + 5 * 60_000
        while (part.length() < 30L * 1024 * 1024 && System.currentTimeMillis() < deadline) Thread.sleep(500)
        Log.i(TAG, "leaving with ${part.length()} bytes downloaded")
        assertTrue(part.length() > 0)
    }

    @Test
    fun resumesAfterRestart() {
        val file = storage.fetcher.finalFile(variant)
        val deadline = System.currentTimeMillis() + 10 * 60_000
        while (!file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(1_000)
        assertTrue("model not downloaded in time", file.exists())
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        Log.i(TAG, "finished: ${file.length()} bytes, sha256 $sha")
        assertTrue(sha == variant.sha256 && file.length() == variant.sizeBytes)
        file.delete()
    }

    private companion object {
        const val TAG = "ModelDownloadResume"
    }
}
