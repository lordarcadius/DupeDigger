package com.vipuljha.dupedigger.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.vipuljha.dupedigger.data.local.db.entity.DuplicateGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.EmbeddingEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.local.db.entity.GroupMemberEntity
import com.vipuljha.dupedigger.data.local.db.entity.PerceptualHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.ScanRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileIndexDao {
    @Query("SELECT * FROM file_index WHERE sourceUri = :sourceUri")
    suspend fun findBySourceUri(sourceUri: String): FileIndexEntity?

    @Query("SELECT * FROM file_index WHERE sourceUri IN (:sourceUris)")
    suspend fun findBySourceUris(sourceUris: List<String>): List<FileIndexEntity>

    @Query("SELECT * FROM file_index WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<FileIndexEntity>

    @Query("DELETE FROM file_index WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Rows are updated in place, never replaced, so cached hashes keyed by the row id survive. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: FileIndexEntity): Long

    @Update
    suspend fun update(file: FileIndexEntity)

    @Query("UPDATE file_index SET lastSeenScanId = :scanId WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<Long>, scanId: Long)

    // substr instead of LIKE, because `_` and `%` occur in URIs.
    @Query("DELETE FROM file_index WHERE lastSeenScanId < :scanId AND substr(sourceUri, 1, length(:uriPrefix)) = :uriPrefix")
    suspend fun deleteNotSeenSince(scanId: Long, uriPrefix: String): Int

    @Query("SELECT COUNT(*) FROM file_index")
    fun observeCount(): Flow<Int>
}

@Dao
interface FileHashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hash: FileHashEntity)

    @Query("SELECT * FROM file_hash WHERE fileId = :fileId")
    suspend fun get(fileId: Long): FileHashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(hashes: List<FileHashEntity>)

    @Query("DELETE FROM file_hash WHERE fileId = :fileId")
    suspend fun deleteForFile(fileId: Long)

    @Query(
        "SELECT f.id, f.sourceUri, f.size, f.mtime, h.partialHash, h.fullHash FROM file_index f " +
            "LEFT JOIN file_hash h ON h.fileId = f.id WHERE f.lastSeenScanId = :scanId",
    )
    suspend fun hashStateForScan(scanId: Long): List<FileHashState>
}

data class KindReclaimRow(val mediaKind: String, val groupCount: Int, val files: Int, val bytes: Long)

data class GroupHeaderRow(val groupId: Long, val fingerprint: String, val tier: String, val score: Float, val mediaKind: String, val keepChoice: String?)

data class TierCountRow(val tier: String, val groupCount: Int)

data class GroupDetailRow(
    val groupId: Long,
    val fingerprint: String,
    val tier: String,
    val score: Float,
    val keepChoice: String?,
    val fileId: Long,
    val sourceUri: String,
    val path: String?,
    val name: String,
    val size: Long,
    val mtime: Long,
    val mediaKind: String,
    val quality: Long?,
)

data class GroupMemberRow(
    val groupId: Long,
    val fileId: Long,
    val sourceUri: String,
    val path: String?,
    val name: String,
    val size: Long,
    val mtime: Long,
    val quality: Long?,
)

data class FileHashState(
    val id: Long,
    val sourceUri: String,
    val size: Long,
    val mtime: Long,
    val partialHash: String?,
    val fullHash: String?,
)

@Dao
interface PerceptualHashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(hashes: List<PerceptualHashEntity>)

    @Query("SELECT * FROM perceptual_hash WHERE algo = :algo")
    suspend fun byAlgo(algo: String): List<PerceptualHashEntity>

    @Query("DELETE FROM perceptual_hash WHERE fileId = :fileId")
    suspend fun deleteForFile(fileId: Long)

    @Query(
        "SELECT f.id, f.sourceUri, f.size, d.value AS dHash, p.value AS pHash FROM file_index f " +
            "LEFT JOIN perceptual_hash d ON d.fileId = f.id AND d.algo = 'DHASH' AND d.frameIndex = 0 " +
            "LEFT JOIN perceptual_hash p ON p.fileId = f.id AND p.algo = 'PHASH' AND p.frameIndex = 0 " +
            "WHERE f.lastSeenScanId = :scanId AND f.mediaKind = 'IMAGE'",
    )
    suspend fun imagesForScan(scanId: Long): List<ImageHashRow>

    /** One row per stored signature part (duration, then frames), or one row with no part for an unsigned video. */
    @Query(
        "SELECT f.id, f.sourceUri, f.size, p.algo, p.frameIndex, p.value FROM file_index f " +
            "LEFT JOIN perceptual_hash p ON p.fileId = f.id AND p.algo IN ('VIDEO_DURATION', 'VIDEO_PHASH') " +
            "WHERE f.lastSeenScanId = :scanId AND f.mediaKind = 'VIDEO' ORDER BY f.id, p.frameIndex",
    )
    suspend fun videosForScan(scanId: Long): List<VideoHashRow>

    @Query(
        "SELECT f.id, f.sourceUri, f.name, f.mimeType, f.size, p.value AS simHash FROM file_index f " +
            "LEFT JOIN perceptual_hash p ON p.fileId = f.id AND p.algo = 'SIMHASH' AND p.frameIndex = 0 " +
            "WHERE f.lastSeenScanId = :scanId AND f.mediaKind = 'DOCUMENT'",
    )
    suspend fun documentsForScan(scanId: Long): List<DocumentHashRow>

    /** One row per stored signature part, or one row with no part for an unread file. */
    @Query(
        "SELECT f.id, f.sourceUri, f.name, f.size, p.algo, p.value FROM file_index f " +
            "LEFT JOIN perceptual_hash p ON p.fileId = f.id AND p.algo IN ('AUDIO_DURATION', 'AUDIO_TITLE', 'AUDIO_ARTIST') " +
            "WHERE f.lastSeenScanId = :scanId AND f.mediaKind = 'AUDIO' ORDER BY f.id",
    )
    suspend fun audioForScan(scanId: Long): List<AudioHashRow>
}

data class DocumentHashRow(val id: Long, val sourceUri: String, val name: String, val mimeType: String?, val size: Long, val simHash: Long?)

data class AudioHashRow(val id: Long, val sourceUri: String, val name: String, val size: Long, val algo: String?, val value: Long?)

data class VideoHashRow(val id: Long, val sourceUri: String, val size: Long, val algo: String?, val frameIndex: Int?, val value: Long?)

data class ImageHashRow(val id: Long, val sourceUri: String, val size: Long, val dHash: Long?, val pHash: Long?)

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: EmbeddingEntity)

    @Query("SELECT * FROM embedding WHERE modelId = :modelId")
    suspend fun forModel(modelId: String): List<EmbeddingEntity>

    @Query("DELETE FROM embedding WHERE fileId = :fileId")
    suspend fun deleteForFile(fileId: Long)

    @Query("DELETE FROM embedding WHERE modelId != :modelId")
    suspend fun deleteOtherModels(modelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(embeddings: List<EmbeddingEntity>)

    @Query(
        "SELECT f.id, f.sourceUri, f.name, f.mimeType, f.mediaKind, f.size, e.vector FROM file_index f " +
            "LEFT JOIN embedding e ON e.fileId = f.id AND e.modelId = :modelKey " +
            "WHERE f.lastSeenScanId = :scanId AND f.mediaKind IN (:kinds)",
    )
    suspend fun candidates(scanId: Long, modelKey: String, kinds: List<String>): List<EmbeddingCandidateRow>
}

class EmbeddingCandidateRow(
    val id: Long,
    val sourceUri: String,
    val name: String,
    val mimeType: String?,
    val mediaKind: String,
    val size: Long,
    val vector: ByteArray?,
)

@Dao
interface DuplicateGroupDao {
    @Insert
    suspend fun insertGroup(group: DuplicateGroupEntity): Long

    @Insert
    suspend fun insertGroups(groups: List<DuplicateGroupEntity>): List<Long>

    @Insert
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM duplicate_group")
    suspend fun deleteAll()

    // Per group, everything but the largest copy can be freed; groups pruned below two members don't count.
    @Query(
        """
        SELECT mediaKind, COUNT(*) AS groupCount, SUM(members - 1) AS files, SUM(total - largest) AS bytes FROM (
            SELECT MIN(f.mediaKind) AS mediaKind, COUNT(*) AS members, SUM(f.size) AS total, MAX(f.size) AS largest
            FROM group_member m JOIN file_index f ON f.id = m.fileId
            GROUP BY m.groupId HAVING COUNT(*) >= 2
        ) GROUP BY mediaKind
        """,
    )
    fun observeReclaimable(): Flow<List<KindReclaimRow>>

    /** Groups with two or more members, counted per tier. */
    @Query(
        """
        SELECT tier, COUNT(*) AS groupCount FROM (
            SELECT g.tier AS tier FROM duplicate_group g JOIN group_member m ON m.groupId = g.groupId
            GROUP BY g.groupId HAVING COUNT(*) >= 2
        ) GROUP BY tier
        """,
    )
    fun observeTierCounts(): Flow<List<TierCountRow>>

    /** Groups with two or more members, most space to free first; a [limit] of -1 returns them all. */
    @Query(
        """
        SELECT g.groupId AS groupId, g.fingerprint AS fingerprint, g.tier AS tier, g.score AS score, MIN(f.mediaKind) AS mediaKind,
            kc.keeperSourceUri AS keepChoice
        FROM duplicate_group g
        JOIN group_member m ON m.groupId = g.groupId
        JOIN file_index f ON f.id = m.fileId
        LEFT JOIN keep_choice kc ON kc.fingerprint = g.fingerprint
        WHERE (:tier IS NULL OR g.tier = :tier)
        GROUP BY g.groupId
        HAVING COUNT(*) >= 2 AND (:kind IS NULL OR MIN(f.mediaKind) = :kind)
        ORDER BY SUM(f.size) - MAX(f.size) DESC, g.groupId
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun groupHeaders(kind: String?, tier: String?, limit: Int, offset: Int): List<GroupHeaderRow>

    @Query(
        """
        SELECT m.groupId AS groupId, f.id AS fileId, f.sourceUri AS sourceUri, f.path AS path, f.name AS name,
            f.size AS size, f.mtime AS mtime, q.value AS quality
        FROM group_member m JOIN file_index f ON f.id = m.fileId
        LEFT JOIN perceptual_hash q ON q.fileId = f.id AND q.algo = 'QUALITY' AND q.frameIndex = 0
        WHERE m.groupId IN (:groupIds)
        """,
    )
    suspend fun membersOfGroups(groupIds: List<Long>): List<GroupMemberRow>

    @Query(
        """
        SELECT g.groupId AS groupId, g.fingerprint AS fingerprint, g.tier AS tier, g.score AS score, kc.keeperSourceUri AS keepChoice,
            f.id AS fileId, f.sourceUri AS sourceUri, f.path AS path, f.name AS name, f.size AS size, f.mtime AS mtime,
            f.mediaKind AS mediaKind, q.value AS quality
        FROM duplicate_group g
        JOIN group_member m ON m.groupId = g.groupId
        JOIN file_index f ON f.id = m.fileId
        LEFT JOIN keep_choice kc ON kc.fingerprint = g.fingerprint
        LEFT JOIN perceptual_hash q ON q.fileId = f.id AND q.algo = 'QUALITY' AND q.frameIndex = 0
        WHERE g.fingerprint = :fingerprint
        """,
    )
    fun observeGroupDetail(fingerprint: String): Flow<List<GroupDetailRow>>

    @Query("DELETE FROM duplicate_group WHERE fingerprint = :fingerprint")
    suspend fun deleteByFingerprint(fingerprint: String)

    @Transaction
    suspend fun insertGroupWithMembers(group: DuplicateGroupEntity, members: List<GroupMemberEntity>): Long {
        val groupId = insertGroup(group)
        insertMembers(members.map { it.copy(groupId = groupId) })
        return groupId
    }

    @Query("SELECT * FROM duplicate_group WHERE scanId = :scanId")
    fun observeForScan(scanId: Long): Flow<List<DuplicateGroupEntity>>

    @Query("SELECT * FROM group_member WHERE groupId = :groupId")
    suspend fun membersOf(groupId: Long): List<GroupMemberEntity>

    @Query("SELECT * FROM group_member WHERE groupId IN (SELECT groupId FROM group_member WHERE fileId IN (:fileIds))")
    suspend fun membersOfGroupsContaining(fileIds: List<Long>): List<GroupMemberEntity>

    @Query("DELETE FROM duplicate_group WHERE scanId != :scanId")
    suspend fun deleteOtherScans(scanId: Long)
}

@Dao
interface ScanRunDao {
    @Insert
    suspend fun insert(run: ScanRunEntity): Long

    @Update
    suspend fun update(run: ScanRunEntity)

    @Query("UPDATE scan_run SET status = :status, finishedAt = :finishedAt WHERE status = :running")
    suspend fun closeRunning(status: String, finishedAt: Long, running: String)

    @Query("UPDATE scan_run SET status = :status, finishedAt = :finishedAt, stats = :stats WHERE id = :id")
    suspend fun finish(id: Long, status: String, finishedAt: Long, stats: String)

    @Query("SELECT * FROM scan_run ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<ScanRunEntity?>
}
