package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.ScanScope

interface FileIndexRepository {
    /** Marks [files] as seen in [scanId]; a file whose size or mtime changed loses its cached hashes and embeddings. */
    suspend fun record(files: List<DiscoveredFile>, scanId: Long): IndexStats

    /** Deletes files [scope] can produce that were not seen in [scanId], with everything derived from them. */
    suspend fun removeNotSeen(scope: ScanScope, scanId: Long): Int

    suspend fun filesSeenIn(scanId: Long): List<IndexedFile>

    /** Stores each file's partial and full hash together, replacing what was cached. */
    suspend fun saveHashes(files: List<IndexedFile>)
}
