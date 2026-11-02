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
    /** Where the file came from, so restore can recreate it inside a picked folder without All files access. */
    val sourceUri: String,
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

@Entity(tableName = "scan_preset")
data class ScanPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A `ScopeKind` name. */
    val scopeKind: String,
    /** Tree URIs for FOLDERS, volume-relative paths for PATHS, one per line; empty for the other kinds. */
    val locations: String,
    /** `MediaKind` names, comma-separated. */
    val mediaKinds: String,
    val createdAt: Long,
)
