package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.detector.AudioCandidate
import com.vipuljha.dupedigger.domain.detector.DOCUMENT_SIMHASH_THRESHOLD
import com.vipuljha.dupedigger.domain.detector.audioArtistKey
import com.vipuljha.dupedigger.domain.detector.audioTitleKey
import com.vipuljha.dupedigger.domain.detector.groupNearAudio
import com.vipuljha.dupedigger.domain.detector.groupNearHashes
import com.vipuljha.dupedigger.domain.detector.groupNearImages
import com.vipuljha.dupedigger.domain.detector.groupNearVideos
import com.vipuljha.dupedigger.domain.detector.hammingDistance
import com.vipuljha.dupedigger.domain.detector.simHash
import com.vipuljha.dupedigger.domain.model.AudioSignature
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.NO_TEXT_SIMHASH
import com.vipuljha.dupedigger.domain.model.NearGroup
import com.vipuljha.dupedigger.domain.model.NearScanResult
import com.vipuljha.dupedigger.domain.model.nearFingerprint
import com.vipuljha.dupedigger.domain.repository.AudioTagReader
import com.vipuljha.dupedigger.domain.repository.DocumentTextReader
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.ImageHasher
import com.vipuljha.dupedigger.domain.repository.VideoHasher
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Finds images, videos, documents and audio that look alike among the files seen in a scan, for the NEAR tier.
 * - Each exact group takes part through one member (its lowest id), so byte-identical copies stay in their exact
 *   group and no file's space is counted twice.
 * - Missing work is done per kind and saved as it completes, so a restarted scan redoes no saved work: images,
 *   documents and audio [PARALLELISM] at a time in batches; videos, which decode several frames each, one at a time.
 * - Documents too short to compare are stored as [NO_TEXT_SIMHASH] and never match; formats the device can't read are
 *   skipped. A file that fails to read is counted as unreadable, never failing the scan.
 * - Documents use their own SimHash threshold; audio matches on title, artist and duration (no Hamming threshold).
 */
class FindNearDuplicatesUseCase @Inject constructor(
    private val fileIndex: FileIndexRepository,
    private val imageHasher: ImageHasher,
    private val videoHasher: VideoHasher,
    private val textReader: DocumentTextReader,
    private val tagReader: AudioTagReader,
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
        val (cachedDocs, newDocs) = fileIndex.documentsSeenIn(scanId)
            .filter { it.id !in exactCopies && textReader.supports(it.name, it.mimeType) }
            .partition { it.simHash != null }
        val (cachedAudio, newAudio) = fileIndex.audioSeenIn(scanId).filter { it.id !in exactCopies }.partition { it.signature != null }
        val total = newImages.size + newVideos.size + newDocs.size + newAudio.size
        val attempted = AtomicInteger()
        val step: suspend () -> Unit = { onProgress(attempted.incrementAndGet(), total) }

        val hashedImages = newImages.chunked(BATCH).flatMap { batch ->
            batch.mapConcurrently { image -> imageHasher.hash(image.sourceUri)?.let { image.copy(hashes = it) }.also { step() } }
                .also { done -> fileIndex.saveImageHashes(done.associate { it.id to it.hashes!! }) }
        }
        val signedVideos = newVideos.mapNotNull { video ->
            coroutineContext.ensureActive()
            videoHasher.signature(video.sourceUri)?.let { video.copy(signature = it) }
                ?.also { fileIndex.saveVideoSignatures(mapOf(it.id to it.signature!!)) }
                .also { step() }
        }
        val hashedDocs = newDocs.chunked(BATCH).flatMap { batch ->
            batch.mapConcurrently { doc ->
                textReader.text(doc.sourceUri, doc.name, doc.mimeType)?.let { doc.copy(simHash = simHash(it) ?: NO_TEXT_SIMHASH) }
                    .also { step() }
            }.also { done -> fileIndex.saveDocumentHashes(done.associate { it.id to it.simHash!! }) }
        }
        val readAudio = newAudio.chunked(BATCH).flatMap { batch ->
            batch.mapConcurrently { audio ->
                tagReader.read(audio.sourceUri)?.let { tags ->
                    audio.copy(signature = AudioSignature(tags.durationMs, tags.title?.let(::audioTitleKey), tags.artist?.let(::audioArtistKey)))
                }.also { step() }
            }.also { done -> fileIndex.saveAudioSignatures(done.associate { it.id to it.signature!! }) }
        }

        val images = (cachedImages + hashedImages).associateBy { it.id }
        val videos = (cachedVideos + signedVideos).associateBy { it.id }
        val docs = (cachedDocs + hashedDocs).filter { it.simHash != NO_TEXT_SIMHASH }.associateBy { it.id }
        val audio = (cachedAudio + readAudio).associateBy { it.id }
        val audioCandidates = audio.values.mapNotNull { file ->
            val signature = file.signature!!
            val titleKey = signature.titleKey ?: audioTitleKey(file.name.substringBeforeLast('.'))
            titleKey?.let { file.id to AudioCandidate(signature.durationMs, it, signature.artistKey) }
        }

        val groups = groupNearImages(images.values.map { it.id to it.hashes!! }, threshold).map { ids ->
            val first = images.getValue(ids.first()).hashes!!.pHash
            nearGroup(ids, images.mapValues { it.value.sourceUri }) { hammingDistance(first, images.getValue(it).hashes!!.pHash).toDouble() / 64 }
        } + groupNearVideos(videos.values.map { it.id to it.signature!! }, threshold).map { ids ->
            val first = videos.getValue(ids.first()).signature!!.frames
            nearGroup(ids, videos.mapValues { it.value.sourceUri }) { id ->
                videos.getValue(id).signature!!.frames.zip(first, ::hammingDistance).average() / 64
            }
        } + groupNearHashes(docs.values.map { it.id to it.simHash!! }, DOCUMENT_SIMHASH_THRESHOLD).map { ids ->
            val first = docs.getValue(ids.first()).simHash!!
            nearGroup(ids, docs.mapValues { it.value.sourceUri }) { hammingDistance(first, docs.getValue(it).simHash!!).toDouble() / 64 }
        } + groupNearAudio(audioCandidates).map { ids ->
            val first = audio.getValue(ids.first()).signature!!.durationMs
            nearGroup(ids, audio.mapValues { it.value.sourceUri }) { id ->
                val d = audio.getValue(id).signature!!.durationMs
                abs(d - first).toDouble() / max(d, first)
            }
        }
        val sizes = images.mapValues { it.value.size } + videos.mapValues { it.value.size } +
            docs.mapValues { it.value.size } + audio.mapValues { it.value.size }
        val done = hashedImages.size + signedVideos.size + hashedDocs.size + readAudio.size
        return NearScanResult(
            groups = groups.toImmutableList(),
            sizes = groups.flatMap { it.fileIds }.associateWith(sizes::getValue),
            hashed = done,
            unreadable = total - done,
        )
    }

    /** [difference] is each member's 0–1 distance from the first; the score is 1 minus their mean. */
    private fun nearGroup(ids: List<Long>, uris: Map<Long, String>, difference: (Long) -> Double) = NearGroup(
        fingerprint = nearFingerprint(ids.map(uris::getValue)),
        fileIds = ids.toImmutableList(),
        score = (1 - ids.drop(1).map(difference).average()).toFloat(),
    )

    private companion object {
        const val BATCH = 100
    }
}
