package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.RemovalResult
import com.vipuljha.dupedigger.domain.model.StoredFile

/** Moves files out of the way recoverably: media to the system trash, everything else to quarantine (D-007). */
interface FileRemover {
    /** [onProgress] gets files handled so far and the total, so a long delete can show how far it has got. */
    suspend fun remove(
        files: List<StoredFile>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): RemovalResult

    /** Takes media this app trashed back out of the system trash; returns how many it couldn't. */
    suspend fun untrash(mediaUris: List<String>): Int
}
