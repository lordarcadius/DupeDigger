package com.vipuljha.dupedigger.data.repository

import androidx.room.withTransaction
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.dao.EmbeddingDao
import com.vipuljha.dupedigger.data.local.db.dao.FileHashDao
import com.vipuljha.dupedigger.data.local.db.dao.FileIndexDao
import com.vipuljha.dupedigger.data.local.db.dao.PerceptualHashDao
import com.vipuljha.dupedigger.data.local.db.entity.EmbeddingEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.storage.sourceUriPrefixes
import com.vipuljha.dupedigger.domain.model.AudioFile
import com.vipuljha.dupedigger.domain.model.AudioSignature
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.DocumentFile
import com.vipuljha.dupedigger.domain.model.EmbeddedFile
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.data.local.db.entity.PerceptualHashEntity
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.model.VIDEO_FRAMES
import com.vipuljha.dupedigger.domain.model.VideoFile
import com.vipuljha.dupedigger.domain.model.VideoSignature
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import kotlinx.collections.immutable.toImmutableList
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps `file_index` rows stable across scans so cached work survives.
 * - New file: inserted.
 * - Same size and mtime: only marked seen; its hashes and embeddings stay.
 * - Size or mtime changed: updated in place and its derived rows deleted, so later stages redo it.
 */
@Singleton
class FileIndexRepositoryImpl @Inject constructor(
    private val db: DupeDiggerDatabase,
    private val fileIndexDao: FileIndexDao,
    private val fileHashDao: FileHashDao,
    private val perceptualHashDao: PerceptualHashDao,
    private val embeddingDao: EmbeddingDao,
) : FileIndexRepository {

    override suspend fun record(files: List<DiscoveredFile>, scanId: Long): IndexStats = db.withTransaction {
        val known = fileIndexDao.findBySourceUris(files.map { it.sourceUri }).associateByTo(HashMap()) { it.sourceUri }
        val seen = ArrayList<Long>()
        var added = 0
        var changed = 0
        for (file in files) {
            val row = known[file.sourceUri]
            val entity = file.toEntity(id = row?.id ?: 0, scanId = scanId)
            when {
                row == null -> {
                    known[file.sourceUri] = entity.copy(id = fileIndexDao.insert(entity))
                    added++
                }
                row.size != file.size || row.mtime != file.mtime -> {
                    fileIndexDao.update(entity)
                    fileHashDao.deleteForFile(row.id)
                    perceptualHashDao.deleteForFile(row.id)
                    embeddingDao.deleteForFile(row.id)
                    known[file.sourceUri] = entity
                    changed++
                }
                row.copy(lastSeenScanId = scanId) == entity -> seen += row.id
                else -> fileIndexDao.update(entity)
            }
        }
        if (seen.isNotEmpty()) fileIndexDao.markSeen(seen, scanId)
        IndexStats(added = added, changed = changed, unchanged = files.size - added - changed)
    }

    override suspend fun removeNotSeen(scope: ScanScope, scanId: Long): Int = db.withTransaction {
        sourceUriPrefixes(scope).sumOf { fileIndexDao.deleteNotSeenSince(scanId, it) }
    }

    override suspend fun filesSeenIn(scanId: Long): List<IndexedFile> =
        fileHashDao.hashStateForScan(scanId).map { IndexedFile(it.id, it.sourceUri, it.size, it.mtime, it.partialHash, it.fullHash) }

    override suspend fun saveHashes(files: List<IndexedFile>) {
        if (files.isEmpty()) return
        fileHashDao.upsertAll(files.map { FileHashEntity(it.id, it.partialHash, it.fullHash, computedAtMtime = it.mtime) })
    }

    override suspend fun imagesSeenIn(scanId: Long): List<ImageFile> = perceptualHashDao.imagesForScan(scanId).map {
        ImageFile(it.id, it.sourceUri, it.size, if (it.dHash != null && it.pHash != null) ImageHashes(it.dHash, it.pHash) else null)
    }

    override suspend fun saveImageHashes(hashes: Map<Long, ImageHashes>) {
        if (hashes.isEmpty()) return
        perceptualHashDao.upsertAll(
            hashes.flatMap { (id, h) ->
                listOfNotNull(
                    PerceptualHashEntity(id, ALGO_DHASH, 0, h.dHash),
                    PerceptualHashEntity(id, ALGO_PHASH, 0, h.pHash),
                    h.pixels?.let { PerceptualHashEntity(id, ALGO_QUALITY, 0, it) },
                )
            },
        )
    }

    override suspend fun videosSeenIn(scanId: Long): List<VideoFile> =
        perceptualHashDao.videosForScan(scanId).groupBy { it.id }.map { (id, rows) ->
            val duration = rows.firstOrNull { it.algo == ALGO_VIDEO_DURATION }?.value
            val frames = rows.filter { it.algo == ALGO_VIDEO_PHASH }.sortedBy { it.frameIndex }.map { it.value!! }
            val complete = duration != null && frames.size == VIDEO_FRAMES
            VideoFile(id, rows.first().sourceUri, rows.first().size, if (complete) VideoSignature(duration!!, frames.toImmutableList()) else null)
        }

    override suspend fun saveVideoSignatures(signatures: Map<Long, VideoSignature>) {
        if (signatures.isEmpty()) return
        perceptualHashDao.upsertAll(
            signatures.flatMap { (id, s) ->
                listOfNotNull(PerceptualHashEntity(id, ALGO_VIDEO_DURATION, 0, s.durationMs), s.pixels?.let { PerceptualHashEntity(id, ALGO_QUALITY, 0, it) }) +
                    s.frames.mapIndexed { i, frame -> PerceptualHashEntity(id, ALGO_VIDEO_PHASH, i, frame) }
            },
        )
    }

    override suspend fun documentsSeenIn(scanId: Long): List<DocumentFile> = perceptualHashDao.documentsForScan(scanId).map {
        DocumentFile(it.id, it.sourceUri, it.name, it.mimeType, it.size, it.simHash)
    }

    override suspend fun saveDocumentHashes(hashes: Map<Long, Long>) {
        if (hashes.isEmpty()) return
        perceptualHashDao.upsertAll(hashes.map { (id, hash) -> PerceptualHashEntity(id, ALGO_SIMHASH, 0, hash) })
    }

    override suspend fun audioSeenIn(scanId: Long): List<AudioFile> =
        perceptualHashDao.audioForScan(scanId).groupBy { it.id }.map { (id, rows) ->
            val part = { algo: String -> rows.firstOrNull { it.algo == algo }?.value }
            val signature = part(ALGO_AUDIO_DURATION)?.let { AudioSignature(it, part(ALGO_AUDIO_TITLE), part(ALGO_AUDIO_ARTIST)) }
            AudioFile(id, rows.first().sourceUri, rows.first().name, rows.first().size, signature)
        }

    override suspend fun saveAudioSignatures(signatures: Map<Long, AudioSignature>) {
        if (signatures.isEmpty()) return
        perceptualHashDao.upsertAll(
            signatures.flatMap { (id, s) ->
                listOfNotNull(
                    PerceptualHashEntity(id, ALGO_AUDIO_DURATION, 0, s.durationMs),
                    s.titleKey?.let { PerceptualHashEntity(id, ALGO_AUDIO_TITLE, 0, it) },
                    s.artistKey?.let { PerceptualHashEntity(id, ALGO_AUDIO_ARTIST, 0, it) },
                    s.bitrate?.let { PerceptualHashEntity(id, ALGO_QUALITY, 0, it) },
                )
            },
        )
    }

    override suspend fun embeddingCandidates(scanId: Long, modelKey: String, kinds: Set<MediaKind>): List<EmbeddedFile> =
        embeddingDao.candidates(scanId, modelKey, kinds.map { it.name }).map {
            EmbeddedFile(it.id, it.sourceUri, it.name, it.mimeType, MediaKind.valueOf(it.mediaKind), it.size, it.vector?.let(::decodeVector))
        }

    override suspend fun saveEmbeddings(modelKey: String, vectors: Map<Long, FloatArray>) {
        if (vectors.isEmpty()) return
        embeddingDao.upsertAll(vectors.map { (id, v) -> EmbeddingEntity(id, modelKey, v.size, encodeVector(v)) })
    }

    override suspend fun deleteEmbeddings(modelId: String) = embeddingDao.deleteForModel(modelId)

    override suspend fun filesByIds(ids: Collection<Long>): List<StoredFile> =
        ids.chunked(MAX_QUERY_ARGS).flatMap { fileIndexDao.findByIds(it) }.map {
            StoredFile(it.id, it.sourceUri, it.path, it.name, MediaKind.valueOf(it.mediaKind), it.size)
        }

    override suspend fun remove(ids: Collection<Long>) = db.withTransaction {
        ids.chunked(MAX_QUERY_ARGS).forEach { fileIndexDao.deleteByIds(it) }
    }

    private fun DiscoveredFile.toEntity(id: Long, scanId: Long) = FileIndexEntity(
        id = id,
        sourceUri = sourceUri,
        path = path,
        name = name,
        mimeType = mimeType,
        mediaKind = mediaKind.name,
        size = size,
        mtime = mtime,
        volume = volume,
        lastSeenScanId = scanId,
    )

    private companion object {
        const val MAX_QUERY_ARGS = 500
        const val ALGO_DHASH = "DHASH"
        const val ALGO_PHASH = "PHASH"
        const val ALGO_VIDEO_PHASH = "VIDEO_PHASH"
        const val ALGO_VIDEO_DURATION = "VIDEO_DURATION"
        const val ALGO_SIMHASH = "SIMHASH"
        const val ALGO_QUALITY = "QUALITY"
        const val ALGO_AUDIO_DURATION = "AUDIO_DURATION"
        const val ALGO_AUDIO_TITLE = "AUDIO_TITLE"
        const val ALGO_AUDIO_ARTIST = "AUDIO_ARTIST"
    }
}

/** Vectors are stored as float32 little-endian (ARCHITECTURE.md). */
internal fun encodeVector(vector: FloatArray): ByteArray =
    ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(vector) }.array()

internal fun decodeVector(bytes: ByteArray): FloatArray =
    FloatArray(bytes.size / Float.SIZE_BYTES).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) }
