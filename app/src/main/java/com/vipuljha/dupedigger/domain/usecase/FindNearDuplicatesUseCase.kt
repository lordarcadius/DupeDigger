package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.detector.groupNearImages
import com.vipuljha.dupedigger.domain.detector.groupNearVideos
import com.vipuljha.dupedigger.domain.detector.hammingDistance
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.NearGroup
import com.vipuljha.dupedigger.domain.model.NearScanResult
import com.vipuljha.dupedigger.domain.model.nearFingerprint
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.ImageHasher
import com.vipuljha.dupedigger.domain.repository.VideoHasher
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Finds images and videos that look alike among the files seen in a scan, for the NEAR tier.
 * - Each exact group takes part through one member (its lowest id), so byte-identical copies stay in their exact
 *   group and no file's space is counted twice.
 * - Missing image hashes are computed [PARALLELISM] at a time and saved per batch; video signatures, which decode
 *   several frames each, one at a time and saved as they come. A restarted scan redoes no saved work.
 * - A file that can't be decoded is skipped and counted, never failing the scan.
 */
class FindNearDuplicatesUseCase @Inject constructor(
    private val fileIndex: FileIndexRepository,
    private val imageHasher: ImageHasher,
    private val videoHasher: VideoHasher,
) {
    /** [onProgress] gets files attempted so far and how many needed work in total; it may be called from several threads. */
    suspend operator fun invoke(
        scanId: Long,
        threshold: Int,
        exactGroups: List<ExactGroup>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): NearScanResult {
        val exactCopies = exactGroups.flatMap { it.fileIds.sorted().drop(1) }.toSet()
        val (cachedImages, newImages) = fileIndex.imagesSeenIn(scanId).filter { it.id !in exactCopies }.partition { it.hashes != null }
        val (cachedVideos, newVideos) = fileIndex.videosSeenIn(scanId).filter { it.id !in exactCopies }.partition { it.signature != null }
        val total = newImages.size + newVideos.size
        val attempted = AtomicInteger()

        val hashedImages = newImages.chunked(BATCH).flatMap { batch ->
            batch.mapConcurrently { image ->
                imageHasher.hash(image.sourceUri)?.let { image.copy(hashes = it) }
                    .also { onProgress(attempted.incrementAndGet(), total) }
            }.also { done -> fileIndex.saveImageHashes(done.associate { it.id to it.hashes!! }) }
        }
        val signedVideos = newVideos.mapNotNull { video ->
            coroutineContext.ensureActive()
            videoHasher.signature(video.sourceUri)?.let { video.copy(signature = it) }
                ?.also { fileIndex.saveVideoSignatures(mapOf(it.id to it.signature!!)) }
                .also { onProgress(attempted.incrementAndGet(), total) }
        }

        val images = (cachedImages + hashedImages).associateBy { it.id }
        val imageGroups = groupNearImages(images.values.map { it.id to it.hashes!! }, threshold).map { ids ->
            val members = ids.map(images::getValue)
            val first = members.first().hashes!!.pHash
            NearGroup(
                nearFingerprint(members.map { it.sourceUri }),
                ids.toImmutableList(),
                similarity(members.drop(1).map { hammingDistance(first, it.hashes!!.pHash).toDouble() }),
            )
        }
        val videos = (cachedVideos + signedVideos).associateBy { it.id }
        val videoGroups = groupNearVideos(videos.values.map { it.id to it.signature!! }, threshold).map { ids ->
            val members = ids.map(videos::getValue)
            val first = members.first().signature!!.frames
            NearGroup(
                nearFingerprint(members.map { it.sourceUri }),
                ids.toImmutableList(),
                similarity(members.drop(1).map { m -> m.signature!!.frames.zip(first, ::hammingDistance).average() }),
            )
        }
        val groups = imageGroups + videoGroups
        val sizes = images.mapValues { it.value.size } + videos.mapValues { it.value.size }
        return NearScanResult(
            groups = groups.toImmutableList(),
            sizes = groups.flatMap { it.fileIds }.associateWith(sizes::getValue),
            hashed = hashedImages.size + signedVideos.size,
            unreadable = total - hashedImages.size - signedVideos.size,
        )
    }

    /** 1 minus the mean distance from the first member, as a share of 64 bits. */
    private fun similarity(distances: List<Double>): Float = (1 - distances.average() / 64).toFloat()

    private companion object {
        const val BATCH = 100
    }
}
