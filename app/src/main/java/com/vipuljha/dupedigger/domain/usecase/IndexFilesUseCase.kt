package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import javax.inject.Inject

/**
 * Brings the file index up to date with one enumeration of a scope.
 * - Writes in batches, one transaction each.
 * - Prunes unseen files only after the enumeration completes, so a cancelled or failed scan keeps its index.
 * - Re-running with the same scan id is safe, which lets an interrupted scan resume.
 */
class IndexFilesUseCase @Inject constructor(
    private val enumerator: FileEnumerator,
    private val fileIndex: FileIndexRepository,
) {
    suspend operator fun invoke(
        scope: ScanScope,
        filter: ScanFilter,
        scanId: Long,
        onProgress: suspend (filesIndexed: Int) -> Unit = {},
    ): IndexStats {
        var stats = IndexStats()
        var indexed = 0
        val batch = ArrayList<DiscoveredFile>(BATCH_SIZE)
        enumerator.enumerate(scope, filter).collect { file ->
            batch += file
            if (batch.size == BATCH_SIZE) {
                stats += fileIndex.record(batch.toList(), scanId)
                indexed += batch.size
                batch.clear()
                onProgress(indexed)
            }
        }
        if (batch.isNotEmpty()) {
            stats += fileIndex.record(batch.toList(), scanId)
            onProgress(indexed + batch.size)
        }
        return stats.copy(removed = fileIndex.removeNotSeen(scope, scanId))
    }

    internal companion object {
        const val BATCH_SIZE = 500
    }
}
