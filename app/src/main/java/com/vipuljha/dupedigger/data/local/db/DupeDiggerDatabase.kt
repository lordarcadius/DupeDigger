package com.vipuljha.dupedigger.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vipuljha.dupedigger.data.local.db.dao.DuplicateGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.EmbeddingDao
import com.vipuljha.dupedigger.data.local.db.dao.ExcludedPathDao
import com.vipuljha.dupedigger.data.local.db.dao.FileHashDao
import com.vipuljha.dupedigger.data.local.db.dao.FileIndexDao
import com.vipuljha.dupedigger.data.local.db.dao.IgnoredGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.KeepChoiceDao
import com.vipuljha.dupedigger.data.local.db.dao.PerceptualHashDao
import com.vipuljha.dupedigger.data.local.db.dao.QuarantineDao
import com.vipuljha.dupedigger.data.local.db.dao.ScanPresetDao
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
import com.vipuljha.dupedigger.data.local.db.entity.ScanPresetEntity
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
        ScanPresetEntity::class,
    ],
    version = 3,
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
    abstract fun scanPresetDao(): ScanPresetDao

    companion object {
        const val NAME = "dupedigger.db"
    }
}

/** Adds `scan_preset` for the user's own scan presets. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scan_preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`scopeKind` TEXT NOT NULL, `locations` TEXT NOT NULL, `mediaKinds` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
    }
}

/** Adds `file_index.fileKey`, the filesystem identity a rename or a move keeps. Existing rows fill it on the next scan. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `file_index` ADD COLUMN `fileKey` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_index_fileKey` ON `file_index` (`fileKey`)")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
