package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.RemovalResult
import com.vipuljha.dupedigger.domain.model.StoredFile

/** Moves files out of the way recoverably: media to the system trash, everything else to quarantine (D-007). */
interface FileRemover {
    suspend fun remove(files: List<StoredFile>): RemovalResult

    /** Takes media this app trashed back out of the system trash; returns how many it couldn't. */
    suspend fun untrash(mediaUris: List<String>): Int
}
