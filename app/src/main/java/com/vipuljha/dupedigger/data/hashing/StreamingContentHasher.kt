package com.vipuljha.dupedigger.data.hashing

import android.content.Context
import com.vipuljha.dupedigger.core.common.DefaultDispatcher
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.common.runSuspendCatching
import com.vipuljha.dupedigger.data.storage.openSource
import com.vipuljha.dupedigger.domain.model.ContentHashes
import com.vipuljha.dupedigger.domain.repository.ContentHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SHA-256 hashes per D-005, read from `file:` or `content:` source URIs.
 * - Partial: the size as 8 bytes, then the first and last 16 KiB.
 * - A file of at most two windows is read once, and its full hash comes with the partial.
 * - Full: streamed through a 256 KiB buffer, checking for cancellation between reads.
 * - Reading more or fewer bytes than the indexed size fails, so a file changed since indexing is never matched.
 */
@Singleton
class StreamingContentHasher(
    private val openStream: (sourceUri: String) -> InputStream,
    private val dispatcher: CoroutineDispatcher,
) : ContentHasher {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this({ uri -> context.contentResolver.openSource(uri) }, dispatcher)

    override suspend fun partialHash(sourceUri: String, size: Long): Outcome<ContentHashes> = hashing {
        openStream(sourceUri).use { input ->
            val partial = sha256().apply { update(size.toBigEndianBytes()) }
            if (size <= 2L * WINDOW) {
                val bytes = input.readExactly(size.toInt())
                input.requireEnd()
                partial.update(bytes)
                ContentHashes(partial.hex(), sha256().digest(bytes).toHex())
            } else {
                partial.update(input.readExactly(WINDOW))
                input.skipExactly(size - 2L * WINDOW)
                partial.update(input.readExactly(WINDOW))
                input.requireEnd()
                ContentHashes(partial.hex(), null)
            }
        }
    }

    override suspend fun fullHash(sourceUri: String, size: Long): Outcome<String> = hashing {
        openStream(sourceUri).use { input ->
            val digest = sha256()
            val buffer = ByteArray(BUFFER)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                total += read
            }
            if (total != size) throw IOException("Expected $size bytes, read $total")
            digest.hex()
        }
    }

    private suspend fun <T> hashing(block: suspend () -> T): Outcome<T> = withContext(dispatcher) {
        runSuspendCatching { block() }.fold(
            onSuccess = { Outcome.Success(it) },
            onFailure = { error ->
                val domainError = when (error) {
                    is FileNotFoundException -> DomainError.NotFound
                    is SecurityException -> DomainError.PermissionDenied
                    else -> DomainError.Io
                }
                Outcome.Failure(domainError, error)
            },
        )
    }

    private companion object {
        const val WINDOW = 16 * 1024
        const val BUFFER = 256 * 1024

        fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

        fun MessageDigest.hex(): String = digest().toHex()

        fun ByteArray.toHex(): String {
            val chars = CharArray(size * 2)
            forEachIndexed { i, byte ->
                chars[i * 2] = HEX[(byte.toInt() shr 4) and 0xF]
                chars[i * 2 + 1] = HEX[byte.toInt() and 0xF]
            }
            return String(chars)
        }

        const val HEX = "0123456789abcdef"

        fun Long.toBigEndianBytes() = ByteArray(8) { i -> (this shr (56 - 8 * i)).toByte() }

        // InputStream.readNBytes and skipNBytes need API 33+.
        fun InputStream.readExactly(count: Int): ByteArray {
            val bytes = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = read(bytes, offset, count - offset)
                if (read < 0) throw EOFException("Expected $count bytes, read $offset")
                offset += read
            }
            return bytes
        }

        fun InputStream.skipExactly(count: Long) {
            var remaining = count
            while (remaining > 0) {
                val skipped = skip(remaining)
                if (skipped > 0) {
                    remaining -= skipped
                } else if (read() < 0) {
                    throw EOFException("Stream ended $remaining bytes early")
                } else {
                    remaining--
                }
            }
        }

        fun InputStream.requireEnd() {
            if (read() >= 0) throw IOException("File is longer than its indexed size")
        }
    }
}
