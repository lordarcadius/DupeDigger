package com.vipuljha.dupedigger.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vipuljha.dupedigger.data.local.db.entity.ExcludedPathEntity
import com.vipuljha.dupedigger.data.local.db.entity.IgnoredGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.KeepChoiceEntity
import com.vipuljha.dupedigger.data.local.db.entity.QuarantineItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredGroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ignored: IgnoredGroupEntity)

    @Query("DELETE FROM ignored_group WHERE fingerprint = :fingerprint")
    suspend fun delete(fingerprint: String)

    @Query("SELECT fingerprint FROM ignored_group")
    fun observeFingerprints(): Flow<List<String>>
}

@Dao
interface KeepChoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(choice: KeepChoiceEntity)

    @Query("SELECT * FROM keep_choice WHERE fingerprint = :fingerprint")
    suspend fun get(fingerprint: String): KeepChoiceEntity?

    @Query("DELETE FROM keep_choice WHERE fingerprint = :fingerprint")
    suspend fun delete(fingerprint: String)
}

@Dao
interface QuarantineDao {
    @Insert
    suspend fun insert(item: QuarantineItemEntity): Long

    @Query("SELECT * FROM quarantine_item WHERE id = :id")
    suspend fun get(id: Long): QuarantineItemEntity?

    @Query("SELECT * FROM quarantine_item ORDER BY movedAt DESC")
    fun observeAll(): Flow<List<QuarantineItemEntity>>

    @Query("DELETE FROM quarantine_item WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExcludedPathDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(path: ExcludedPathEntity)

    @Query("DELETE FROM excluded_path WHERE path = :path")
    suspend fun delete(path: String)

    @Query("SELECT * FROM excluded_path ORDER BY path")
    fun observeAll(): Flow<List<ExcludedPathEntity>>
}
