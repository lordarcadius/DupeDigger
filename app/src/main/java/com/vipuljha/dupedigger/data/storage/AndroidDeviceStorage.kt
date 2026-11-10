package com.vipuljha.dupedigger.data.storage

import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.model.StorageUsage
import com.vipuljha.dupedigger.domain.repository.DeviceStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Space over every mounted volume, read fresh each time so a removed SD card drops out of the total. */
@Singleton
class AndroidDeviceStorage @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val volumes: StorageVolumes,
) : DeviceStorage {

    override suspend fun usage(): StorageUsage = withContext(ioDispatcher) {
        val roots = volumes.roots()
        StorageUsage(
            totalBytes = roots.sumOf { it.totalSpace },
            freeBytes = roots.sumOf { it.usableSpace },
        )
    }
}
