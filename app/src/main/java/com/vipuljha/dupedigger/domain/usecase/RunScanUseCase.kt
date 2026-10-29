package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.common.runSuspendCatching
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.ExactScanResult
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.model.isReachable
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One scan of a scope, recorded as a scan run.
 * - Fails with PermissionDenied before touching the index when the scope can't be read.
 * - Runs the stages in order (index, then exact) and reports progress for each.
 * - Only a scan that completes replaces the saved groups; a cancelled or failed one leaves the previous results.
 * - Every stage caches per file, so a scan restarted after process death redoes nothing it already finished.
 * - The run always ends with a status; if the process dies first, the next run marks it interrupted.
 */
class RunScanUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val excludedPaths: ExcludedPathRepository,
    private val storageAccess: StorageAccess,
    private val scanFolders: ScanFolderRepository,
    private val scanRuns: ScanRunRepository,
    private val indexFiles: IndexFilesUseCase,
    private val findExact: FindExactDuplicatesUseCase,
    private val saveExactGroups: SaveExactGroupsUseCase,
) {
    suspend operator fun invoke(
        scope: ScanScope,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): Outcome<ScanSummary> {
        if (!scope.isReachable(storageAccess.current(), scanFolders.folders.first())) {
            return Outcome.Failure(DomainError.PermissionDenied)
        }
        val appSettings = settings.settings.first()
        val filter = ScanFilter(excludedPaths.paths.first(), appSettings.skipHiddenFiles)
        val scanId = scanRuns.start(scope)
        var status = ScanStatus.CANCELLED
        var summary: ScanSummary? = null
        try {
            val result = runSuspendCatching {
                onProgress(ScanProgress(ScanStage.INDEXING, 0, null))
                val index = indexFiles(scope, filter, scanId) { onProgress(ScanProgress(ScanStage.INDEXING, it, null)) }
                onProgress(ScanProgress(ScanStage.EXACT, 0, null))
                val exact = findExact(scanId, appSettings.minFileSizeBytes) { done, total ->
                    onProgress(ScanProgress(ScanStage.EXACT, done, total))
                }
                exact.toSummary(index, kept = saveExactGroups(scanId, exact.groups))
            }
            summary = result.getOrNull()
            status = if (summary != null) ScanStatus.COMPLETED else ScanStatus.FAILED
            return result.fold(
                onSuccess = { Outcome.Success(it) },
                onFailure = { Outcome.Failure(if (it is SecurityException) DomainError.PermissionDenied else DomainError.Io, it) },
            )
        } finally {
            withContext(NonCancellable) { scanRuns.finish(scanId, status, summary) }
        }
    }

    private fun ExactScanResult.toSummary(index: IndexStats, kept: List<ExactGroup>) = ScanSummary(
        index = index,
        exactGroups = kept.size,
        duplicateFiles = kept.sumOf { it.fileIds.size - 1 },
        reclaimableBytes = kept.sumOf { it.size * (it.fileIds.size - 1) },
        hashesComputed = partialHashed + fullHashed,
        unreadable = unreadable,
    )
}
