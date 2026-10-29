package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.detector.groupNearImages
import com.vipuljha.dupedigger.domain.detector.hammingDistance
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.NearGroup
import com.vipuljha.dupedigger.domain.model.NearScanResult
import com.vipuljha.dupedigger.domain.model.nearFingerprint
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.ImageHasher
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Finds images that look alike among those seen in a scan, for the NEAR tier.
 * - Each exact group takes part through one member (its lowest id), so byte-identical copies stay in their exact
 *   group and no file's space is counted twice.
 * - Missing hashes are computed [PARALLELISM] at a time and saved per batch, so a restarted scan redoes no finished batch.
 * - An image that can't be decoded is skipped and counted, never failing the scan.
 */
class FindNearImagesUseCase @Inject constructor(
    private val fileIndex: FileIndexRepository,
    private val hasher: ImageHasher,
) {
    /** [onProgress] gets images attempted so far and how many needed hashing; it may be called from several threads. */
    suspend operator fun invoke(
        scanId: Long,
        threshold: Int,
        exactGroups: List<ExactGroup>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): NearScanResult {
        val exactCopies = exactGroups.flatMap { it.fileIds.sorted().drop(1) }.toSet()
        val (cached, missing) = fileIndex.imagesSeenIn(scanId).filter { it.id !in exactCopies }.partition { it.hashes != null }
        val attempted = AtomicInteger()
        val hashed = missing.chunked(BATCH).flatMap { batch ->
            batch.mapConcurrently { image ->
                hasher.hash(image.sourceUri)?.let { image.copy(hashes = it) }
                    .also { onProgress(attempted.incrementAndGet(), missing.size) }
            }.also { done -> fileIndex.saveImageHashes(done.associate { it.id to it.hashes!! }) }
        }

        val images = (cached + hashed).associateBy { it.id }
        val groups = groupNearImages(images.values.map { it.id to it.hashes!! }, threshold).map { ids ->
            val members = ids.map(images::getValue)
            NearGroup(nearFingerprint(members.map { it.sourceUri }), ids.toImmutableList(), similarity(members))
        }
        return NearScanResult(
            groups = groups.toImmutableList(),
            sizes = groups.flatMap { it.fileIds }.associateWith { images.getValue(it).size },
            hashed = hashed.size,
            unreadable = missing.size - hashed.size,
        )
    }

    /** 1 minus the mean pHash distance from the first member, as a share of 64 bits. */
    private fun similarity(members: List<ImageFile>): Float {
        val first = members.first().hashes!!.pHash
        val mean = members.drop(1).map { hammingDistance(first, it.hashes!!.pHash) }.average()
        return (1 - mean / 64).toFloat()
    }

    private companion object {
        const val BATCH = 100
    }
}
