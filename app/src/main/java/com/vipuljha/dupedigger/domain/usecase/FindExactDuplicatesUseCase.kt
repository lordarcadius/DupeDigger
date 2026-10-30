package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.getOrNull
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.ExactScanResult
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.repository.ContentHasher
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.atomic.AtomicInteger
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
    /** [onProgress] gets files attempted so far and the total known so far; it may be called from several threads. */
    suspend operator fun invoke(
        scanId: Long,
        minSizeBytes: Long,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExactScanResult {
        val sameSize = fileIndex.filesSeenIn(scanId).filter { it.size >= minSizeBytes }.inSharedBuckets { it.size }

        val (withPartial, needPartial) = sameSize.partition { it.partialHash != null }
        val partials = withPartial.toMutableList()
        var partialHashed = 0
        for ((i, batch) in needPartial.chunked(PARTIAL_BATCH).withIndex()) {
            val hashed = batch.mapConcurrently { file ->
                hasher.partialHash(file.sourceUri, file.size).getOrNull()
                    ?.let { file.copy(partialHash = it.partial, fullHash = it.full) }
            }
            fileIndex.saveHashes(hashed)
            partials += hashed
            partialHashed += hashed.size
            onProgress(minOf((i + 1) * PARTIAL_BATCH, needPartial.size), needPartial.size)
        }

        val samePartial = partials.inSharedBuckets { it.size to it.partialHash }
        val (withFull, needFull) = samePartial.partition { it.fullHash != null }
        val total = needPartial.size + needFull.size
        val attempted = AtomicInteger(needPartial.size)
        val fullyHashed = needFull.mapConcurrently { file ->
            hasher.fullHash(file.sourceUri, file.size).getOrNull()
                ?.let { file.copy(fullHash = it) }
                ?.also { fileIndex.saveHashes(listOf(it)) }
                .also { onProgress(attempted.incrementAndGet(), total) }
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

    internal companion object {
        const val PARTIAL_BATCH = 500
    }
}
