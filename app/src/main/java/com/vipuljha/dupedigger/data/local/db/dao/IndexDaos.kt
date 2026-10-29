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
}

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
}

@Dao
interface DuplicateGroupDao {
    @Insert
    suspend fun insertGroup(group: DuplicateGroupEntity): Long

    @Insert
    suspend fun insertMembers(members: List<GroupMemberEntity>)

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

    @Query("DELETE FROM duplicate_group WHERE scanId != :scanId")
    suspend fun deleteOtherScans(scanId: Long)
}

@Dao
interface ScanRunDao {
    @Insert
    suspend fun insert(run: ScanRunEntity): Long

    @Update
    suspend fun update(run: ScanRunEntity)

    @Query("SELECT * FROM scan_run ORDER BY id DESC LIMIT 1")
    fun observeLatest(): Flow<ScanRunEntity?>
}
