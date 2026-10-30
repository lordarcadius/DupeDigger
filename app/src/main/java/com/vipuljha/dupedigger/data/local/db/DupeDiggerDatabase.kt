package com.vipuljha.dupedigger.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.vipuljha.dupedigger.data.local.db.dao.DuplicateGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.EmbeddingDao
import com.vipuljha.dupedigger.data.local.db.dao.ExcludedPathDao
import com.vipuljha.dupedigger.data.local.db.dao.FileHashDao
import com.vipuljha.dupedigger.data.local.db.dao.FileIndexDao
import com.vipuljha.dupedigger.data.local.db.dao.IgnoredGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.KeepChoiceDao
import com.vipuljha.dupedigger.data.local.db.dao.PerceptualHashDao
import com.vipuljha.dupedigger.data.local.db.dao.QuarantineDao
import com.vipuljha.dupedigger.data.local.db.dao.ScanRunDao
import com.vipuljha.dupedigger.data.local.db.entity.DuplicateGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.EmbeddingEntity
import com.vipuljha.dupedigger.data.local.db.entity.ExcludedPathEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.local.db.entity.GroupMemberEntity
import com.vipuljha.dupedigger.data.local.db.entity.IgnoredGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.KeepChoiceEntity
import com.vipuljha.dupedigger.data.local.db.entity.PerceptualHashEntity
import com.vipuljha.dupedigger.data.local.db.entity.QuarantineItemEntity
import com.vipuljha.dupedigger.data.local.db.entity.ScanRunEntity

/**
 * One database for index and user-decision tables, under the user-data policy:
 * - the schema is exported and committed for every shipped version;
 * - every version bump ships a real migration in [ALL_MIGRATIONS] and a test;
 * - never a destructive fallback, because the user-decision tables hold user choices.
 */
@Database(
    entities = [
        FileIndexEntity::class, FileHashEntity::class, PerceptualHashEntity::class, EmbeddingEntity::class,
        DuplicateGroupEntity::class, GroupMemberEntity::class, ScanRunEntity::class,
        IgnoredGroupEntity::class, KeepChoiceEntity::class, QuarantineItemEntity::class, ExcludedPathEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DupeDiggerDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun fileHashDao(): FileHashDao
    abstract fun perceptualHashDao(): PerceptualHashDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun duplicateGroupDao(): DuplicateGroupDao
    abstract fun scanRunDao(): ScanRunDao
    abstract fun ignoredGroupDao(): IgnoredGroupDao
    abstract fun keepChoiceDao(): KeepChoiceDao
    abstract fun quarantineDao(): QuarantineDao
    abstract fun excludedPathDao(): ExcludedPathDao

    companion object {
        const val NAME = "dupedigger.db"
    }
}

val ALL_MIGRATIONS: Array<Migration> = emptyArray()
