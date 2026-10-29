package com.vipuljha.dupedigger.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Index and cache tables: everything here can be rebuilt by rescanning.

@Entity(
    tableName = "file_index",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["size"]),
        Index(value = ["mediaKind"]),
        Index(value = ["lastSeenScanId"]),
    ],
)
data class FileIndexEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val path: String?,
    val name: String,
    val mimeType: String?,
    val mediaKind: String,
    val size: Long,
    val mtime: Long,
    val volume: String?,
    val lastSeenScanId: Long,
)

@Entity(
    tableName = "file_hash",
    foreignKeys = [ForeignKey(FileIndexEntity::class, ["id"], ["fileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["partialHash"]), Index(value = ["fullHash"])],
)
data class FileHashEntity(
    @PrimaryKey val fileId: Long,
    val partialHash: String?,
    val fullHash: String?,
    val computedAtMtime: Long,
)

@Entity(
    tableName = "perceptual_hash",
    primaryKeys = ["fileId", "algo", "frameIndex"],
    foreignKeys = [ForeignKey(FileIndexEntity::class, ["id"], ["fileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["algo"])],
)
data class PerceptualHashEntity(
    val fileId: Long,
    val algo: String,
    val frameIndex: Int,
    val value: Long,
)

@Entity(
    tableName = "embedding",
    primaryKeys = ["fileId", "modelId"],
    foreignKeys = [ForeignKey(FileIndexEntity::class, ["id"], ["fileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["modelId"])],
)
data class EmbeddingEntity(
    val fileId: Long,
    val modelId: String,
    val dim: Int,
    /** float32 little-endian, L2-normalised. */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val vector: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is EmbeddingEntity &&
        fileId == other.fileId && modelId == other.modelId && dim == other.dim && vector.contentEquals(other.vector)

    override fun hashCode(): Int = ((fileId.hashCode() * 31 + modelId.hashCode()) * 31 + dim) * 31 + vector.contentHashCode()
}

@Entity(
    tableName = "duplicate_group",
    indices = [Index(value = ["scanId"]), Index(value = ["tier"]), Index(value = ["fingerprint"])],
)
data class DuplicateGroupEntity(
    @PrimaryKey(autoGenerate = true) val groupId: Long = 0,
    val tier: String,
    val score: Float,
    val scanId: Long,
    val fingerprint: String,
)

@Entity(
    tableName = "group_member",
    primaryKeys = ["groupId", "fileId"],
    foreignKeys = [
        ForeignKey(DuplicateGroupEntity::class, ["groupId"], ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(FileIndexEntity::class, ["id"], ["fileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["fileId"])],
)
data class GroupMemberEntity(
    val groupId: Long,
    val fileId: Long,
    val similarity: Float,
)

@Entity(tableName = "scan_run")
data class ScanRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val tiers: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: String,
    /** Small kotlinx JSON object with the run's counters. */
    val stats: String,
)
