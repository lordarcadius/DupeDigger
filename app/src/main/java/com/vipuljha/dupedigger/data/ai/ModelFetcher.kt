package com.vipuljha.dupedigger.data.ai

import com.vipuljha.dupedigger.domain.model.ModelDownloadFailure
import com.vipuljha.dupedigger.domain.model.ModelVariant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface FetchResult {
    data class Done(val file: File) : FetchResult

    /** [retryable] failures (network, server busy) resume from the partial file on the next attempt. */
    data class Failed(val reason: ModelDownloadFailure, val retryable: Boolean) : FetchResult
}

/**
 * Downloads a model into [dir] as `<file>.part`, resuming with an HTTP Range request, and only renames it to its final
 * name once its size and SHA-256 match, so an existing final file is always a verified one.
 * - A 206 starting at the partial size appends; a 200 or a 206 from elsewhere starts over; a 416 means the partial
 *   file may already be whole, so it is checked.
 * - A checksum mismatch deletes the partial file, so it is never resumed. A stop, cancel or short transfer keeps it.
 * - There must be room for the rest of the file plus [SPACE_MARGIN_BYTES].
 */
class ModelFetcher(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val freeBytes: (File) -> Long = { it.usableSpace },
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun finalFile(variant: ModelVariant) = File(dir, variant.fileName)

    fun partFile(variant: ModelVariant) = File(dir, "${variant.fileName}.${variant.sha256.take(12)}.part")

    suspend fun fetch(
        variant: ModelVariant,
        onProgress: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): FetchResult = withContext(ioDispatcher) { fetchNow(variant, onProgress) }

    private suspend fun fetchNow(variant: ModelVariant, onProgress: suspend (Long, Long) -> Unit): FetchResult {
        val installed = finalFile(variant)
        if (installed.length() == variant.sizeBytes) return FetchResult.Done(installed)
        dir.mkdirs()
        val part = partFile(variant)
        if (part.length() > variant.sizeBytes) part.delete()
        if (part.length() < variant.sizeBytes) {
            if (freeBytes(dir) < variant.sizeBytes - part.length() + SPACE_MARGIN_BYTES) {
                return FetchResult.Failed(ModelDownloadFailure.NO_SPACE, retryable = false)
            }
            transfer(variant, part, onProgress)?.let { return it }
            // A connection closed early ends the stream without an error; keep what arrived and resume.
            if (part.length() < variant.sizeBytes) return FetchResult.Failed(ModelDownloadFailure.NETWORK, retryable = true)
        }
        return verify(variant, part)
    }

    /** Null when the partial file now holds the whole download (or the server says it already does). */
    private suspend fun transfer(variant: ModelVariant, part: File, onProgress: suspend (Long, Long) -> Unit): FetchResult? {
        val offset = part.length()
        val connection = open(URL(variant.url))
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val code = connection.responseCode
            val append = when {
                code == HttpURLConnection.HTTP_PARTIAL -> connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true
                code == HTTP_RANGE_NOT_SATISFIABLE -> return null
                code == HttpURLConnection.HTTP_OK -> false
                code == TOO_MANY_REQUESTS || code >= HttpURLConnection.HTTP_INTERNAL_ERROR ->
                    return FetchResult.Failed(ModelDownloadFailure.NETWORK, retryable = true)
                else -> return FetchResult.Failed(ModelDownloadFailure.HTTP, retryable = false)
            }
            Timber.i("Model %s: HTTP %d, Content-Range %s, %s at byte %d", variant.id, code, connection.getHeaderField("Content-Range"), if (append) "appending" else "restarting", if (append) offset else 0L)
            var downloaded = if (append) offset else 0L
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastReported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = downloaded
                            onProgress(downloaded, variant.sizeBytes)
                        }
                    }
                    output.fd.sync()
                }
            }
            onProgress(downloaded, variant.sizeBytes)
            return null
        } catch (e: IOException) {
            return FetchResult.Failed(ModelDownloadFailure.NETWORK, retryable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(variant: ModelVariant, part: File): FetchResult {
        if (part.length() != variant.sizeBytes || sha256(part) != variant.sha256) {
            part.delete()
            return FetchResult.Failed(ModelDownloadFailure.CHECKSUM, retryable = false)
        }
        val target = finalFile(variant)
        return if (part.renameTo(target)) FetchResult.Done(target) else FetchResult.Failed(ModelDownloadFailure.NETWORK, retryable = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_BYTES = 256 * 1024
        private const val PROGRESS_STEP_BYTES = 1L * 1024 * 1024
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val TOO_MANY_REQUESTS = 429
    }
}
