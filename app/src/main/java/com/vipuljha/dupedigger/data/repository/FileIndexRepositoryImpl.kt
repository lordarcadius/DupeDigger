package com.vipuljha.dupedigger.data.repository

import androidx.room.withTransaction
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.dao.EmbeddingDao
import com.vipuljha.dupedigger.data.local.db.dao.FileHashDao
import com.vipuljha.dupedigger.data.local.db.dao.FileIndexDao
import com.vipuljha.dupedigger.data.local.db.dao.PerceptualHashDao
import com.vipuljha.dupedigger.data.local.db.entity.FileHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.storage.sourceUriPrefixes
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.data.local.db.entity.PerceptualHashEntity
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
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
                listOf(PerceptualHashEntity(id, ALGO_DHASH, 0, h.dHash), PerceptualHashEntity(id, ALGO_PHASH, 0, h.pHash))
            },
        )
    }

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
    }
}
