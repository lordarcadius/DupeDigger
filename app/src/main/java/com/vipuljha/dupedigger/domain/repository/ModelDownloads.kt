package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.ModelDownloadState
import com.vipuljha.dupedigger.domain.model.ModelVariant
import kotlinx.coroutines.flow.Flow

/** Downloads model files in the background, one at a time, resuming where an interrupted download stopped. */
interface ModelDownloads {
    /** Queues the download; with [wifiOnly] it waits for an unmetered network. Already queued or running work is kept. */
    fun start(variant: ModelVariant, wifiOnly: Boolean)

    /** Stops the download and throws away what was fetched so far. */
    fun cancel(variant: ModelVariant)

    /** Removes the downloaded model file. */
    suspend fun delete(variant: ModelVariant)

    fun observe(variant: ModelVariant): Flow<ModelDownloadState>

    /** The verified model file's path, or null when it isn't downloaded. */
    fun installedPath(variant: ModelVariant): String?
}
