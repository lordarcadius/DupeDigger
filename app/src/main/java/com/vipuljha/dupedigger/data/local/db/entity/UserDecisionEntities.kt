package com.vipuljha.dupedigger.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// User-decision tables: user data, never dropped. They don't reference the rebuildable index tables.

@Entity(tableName = "ignored_group")
data class IgnoredGroupEntity(
    @PrimaryKey val fingerprint: String,
    val ignoredAt: Long,
)

@Entity(tableName = "keep_choice")
data class KeepChoiceEntity(
    @PrimaryKey val fingerprint: String,
    val keeperSourceUri: String,
    val chosenAt: Long,
)

@Entity(tableName = "quarantine_item", indices = [Index(value = ["movedAt"])])
data class QuarantineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val quarantinePath: String,
    val size: Long,
    val movedAt: Long,
)

@Entity(tableName = "excluded_path")
data class ExcludedPathEntity(
    @PrimaryKey val path: String,
    val addedAt: Long,
)
