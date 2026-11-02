package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.ScanPreset
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/** The user's own presets; built-in presets are not stored. */
interface ScanPresetRepository {
    /** Oldest first; picked folders whose grant was released are left out of a preset's scope. */
    val presets: Flow<ImmutableList<ScanPreset>>

    /** Inserts a preset whose id is 0 and returns its new id; otherwise replaces that preset's name, scope and types. */
    suspend fun save(preset: ScanPreset): Long

    suspend fun delete(id: Long)
}
