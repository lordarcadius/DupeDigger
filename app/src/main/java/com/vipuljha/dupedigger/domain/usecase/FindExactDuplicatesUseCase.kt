package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.getOrNull
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.ExactScanResult
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.repository.ContentHasher
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Finds byte-identical files among every indexed file of the chosen kinds, reading as little as possible.
 * - A file an earlier scan indexed takes part too, so a copy is found wherever on the device it sits. Reading one
 *   costs nothing extra in the common case: a file only gets opened once something in this scan shares its size.
 * - Files under the minimum size, or with a size no other file has, are never opened.
 * - A partial hash, then a full hash, each only within buckets keyed by size and hash that still hold two or more files.
 * - Cached hashes are reused; new ones are written a batch at a time, and a cancelled stage still flushes what it read,
 *   so a scan stopped part-way never repeats a read on the next run.
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
        kinds: Set<MediaKind> = AllMediaKinds,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExactScanResult {
        val sameSize = fileIndex.indexedFiles(scanId, kinds).filter { it.size >= minSizeBytes }
            .inSharedBuckets { it.size }

        val (withPartial, needPartial) = sameSize.partition { it.partialHash != null }
        val partials = withPartial.toMutableList()
        var partialHashed = 0
        val unsaved = ConcurrentLinkedQueue<IndexedFile>()
        try {
            for ((i, batch) in needPartial.chunked(PARTIAL_BATCH).withIndex()) {
                val hashed = batch.mapConcurrently { file ->
                    hasher.partialHash(file.sourceUri, file.size).getOrNull()
                        ?.let { file.copy(partialHash = it.partial, fullHash = it.full) }
                        ?.also(unsaved::add)
                }
                flush(unsaved)
                partials += hashed
                partialHashed += hashed.size
                onProgress(minOf((i + 1) * PARTIAL_BATCH, needPartial.size), needPartial.size)
            }
        } finally {
            withContext(NonCancellable) { flush(unsaved) }
        }

        val samePartial = partials.inSharedBuckets { it.size to it.partialHash }
        val (withFull, needFull) = samePartial.partition { it.fullHash != null }
        val total = needPartial.size + needFull.size
        val attempted = AtomicInteger(needPartial.size)
        val fullyHashed = ArrayList<IndexedFile>(needFull.size)
        try {
            for (batch in needFull.chunked(FULL_BATCH)) {
                fullyHashed += batch.mapConcurrently { file ->
                    hasher.fullHash(file.sourceUri, file.size).getOrNull()
                        ?.let { file.copy(fullHash = it) }
                        ?.also(unsaved::add)
                        .also { onProgress(attempted.incrementAndGet(), total) }
                }
                flush(unsaved)
            }
        } finally {
            withContext(NonCancellable) { flush(unsaved) }
        }

        val groups = (withFull + fullyHashed)
            .groupBy { it.size to it.fullHash!! }
            .filterValues { it.size > 1 }
            .map { (key, files) ->
                ExactGroup(
                    key.first,
                    key.second,
                    files.map { it.id }.sorted().toImmutableList()
                )
            }
            .sortedWith(compareByDescending<ExactGroup> { it.size }.thenBy { it.fullHash })
        return ExactScanResult(
            groups = groups.toImmutableList(),
            partialHashed = partialHashed,
            fullHashed = fullyHashed.size,
            unreadable = (needPartial.size - partialHashed) + (needFull.size - fullyHashed.size),
        )
    }

    /** Writes every hash computed since the last call, in one go; a cancelled stage flushes what it already read. */
    private suspend fun flush(hashed: ConcurrentLinkedQueue<IndexedFile>) {
        val batch = generateSequence { hashed.poll() }.toList()
        if (batch.isNotEmpty()) fileIndex.saveHashes(batch)
    }

    private fun <K> List<IndexedFile>.inSharedBuckets(key: (IndexedFile) -> K): List<IndexedFile> =
        groupBy(key).values.filter { it.size > 1 }.flatten()

    internal companion object {
        const val PARTIAL_BATCH = 500

        /** Full hashes cost a whole read each, so they are saved in smaller batches than partials. */
        const val FULL_BATCH = 100
    }
}
