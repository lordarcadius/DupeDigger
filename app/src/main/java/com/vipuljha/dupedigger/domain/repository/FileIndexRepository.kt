package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile

interface FileIndexRepository {
    /** Marks [files] as seen in [scanId]; a file whose size or mtime changed loses its cached hashes and embeddings. */
    suspend fun record(files: List<DiscoveredFile>, scanId: Long): IndexStats

    /** Deletes files [scope] can produce that were not seen in [scanId], with everything derived from them. */
    suspend fun removeNotSeen(scope: ScanScope, scanId: Long): Int

    suspend fun filesSeenIn(scanId: Long): List<IndexedFile>

    /** Stores each file's partial and full hash together, replacing what was cached. */
    suspend fun saveHashes(files: List<IndexedFile>)

    suspend fun filesByIds(ids: Collection<Long>): List<StoredFile>

    /** Deletes the files' rows and everything derived from them, including group memberships. */
    suspend fun remove(ids: Collection<Long>)
}
