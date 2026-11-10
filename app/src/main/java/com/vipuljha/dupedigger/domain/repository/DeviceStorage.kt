package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.StorageUsage

interface DeviceStorage {
    /** Total and free space over every mounted volume; zeroes when none can be read. */
    suspend fun usage(): StorageUsage
}
