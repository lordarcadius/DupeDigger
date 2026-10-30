package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** Applies [transform] to the stored settings atomically. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
