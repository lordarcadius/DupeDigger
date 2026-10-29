package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.getOrNull
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.ExactScanResult
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.repository.ContentHasher
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Finds byte-identical files among those seen in a scan, reading as little as possible.
 * - Files under the minimum size, or with a size no other file has, are never opened.
 * - A partial hash, then a full hash, each only within buckets keyed by size and hash that still hold two or more files.
 * - Cached hashes are reused; new partials are saved per batch and each full hash as soon as it is computed.
 * - A file that can't be read is skipped and counted, never failing the scan.
 */
class FindExactDuplicatesUseCase @Inject constructor(
    private val fileIndex: FileIndexRepository,
    private val hasher: ContentHasher,
) {
    suspend operator fun invoke(scanId: Long, minSizeBytes: Long): ExactScanResult {
        val sameSize = fileIndex.filesSeenIn(scanId).filter { it.size >= minSizeBytes }.inSharedBuckets { it.size }

        val (withPartial, needPartial) = sameSize.partition { it.partialHash != null }
        val partials = withPartial.toMutableList()
        var partialHashed = 0
        for (batch in needPartial.chunked(PARTIAL_BATCH)) {
            val hashed = batch.mapConcurrently { file ->
                hasher.partialHash(file.sourceUri, file.size).getOrNull()
                    ?.let { file.copy(partialHash = it.partial, fullHash = it.full) }
            }
            fileIndex.saveHashes(hashed)
            partials += hashed
            partialHashed += hashed.size
        }

        val samePartial = partials.inSharedBuckets { it.size to it.partialHash }
        val (withFull, needFull) = samePartial.partition { it.fullHash != null }
        val fullyHashed = needFull.mapConcurrently { file ->
            hasher.fullHash(file.sourceUri, file.size).getOrNull()
                ?.let { file.copy(fullHash = it) }
                ?.also { fileIndex.saveHashes(listOf(it)) }
        }

        val groups = (withFull + fullyHashed)
            .groupBy { it.size to it.fullHash!! }
            .filterValues { it.size > 1 }
            .map { (key, files) -> ExactGroup(key.first, key.second, files.map { it.id }.sorted().toImmutableList()) }
            .sortedWith(compareByDescending<ExactGroup> { it.size }.thenBy { it.fullHash })
        return ExactScanResult(
            groups = groups.toImmutableList(),
            partialHashed = partialHashed,
            fullHashed = fullyHashed.size,
            unreadable = (needPartial.size - partialHashed) + (needFull.size - fullyHashed.size),
        )
    }

    private fun <K> List<IndexedFile>.inSharedBuckets(key: (IndexedFile) -> K): List<IndexedFile> =
        groupBy(key).values.filter { it.size > 1 }.flatten()

    /** Runs [transform] on at most [PARALLELISM] items at once and keeps the non-null results, in no particular order. */
    private suspend fun <T, R : Any> List<T>.mapConcurrently(transform: suspend (T) -> R?): List<R> = coroutineScope {
        val queue = Channel<T>(Channel.UNLIMITED)
        forEach { queue.trySend(it) }
        queue.close()
        List(minOf(PARALLELISM, size)) {
            async {
                val results = ArrayList<R>()
                for (item in queue) transform(item)?.let(results::add)
                results
            }
        }.awaitAll().flatten()
    }

    internal companion object {
        const val PARALLELISM = 4
        const val PARTIAL_BATCH = 500
    }
}
