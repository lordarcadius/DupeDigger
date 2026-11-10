package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.common.runSuspendCatching
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.ExactScanResult
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.NearScanResult
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.model.isReachable
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.PowerState
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.SemanticStageSkip
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One scan of a scope, recorded as a scan run.
 * - Fails with PermissionDenied before touching the index when the scope can't be read.
 * - Indexes the whole scope, then looks for duplicates only among files of the chosen kinds, so other files keep
 *   their index rows and cached work.
 * - Runs the stages in order (index, exact, near when similar files are on, semantic when the AI tier is on and its
 *   model is chosen, unless it is set to run only while charging and the device isn't) and reports progress for each.
 * - A skip asked for during the scan ends only the semantic stage; the scan completes with its exact and near groups.
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
    private val findNear: FindNearDuplicatesUseCase,
    private val findSemantic: FindSemanticDuplicatesUseCase,
    private val power: PowerState,
    private val saveGroups: SaveGroupsUseCase,
    private val skip: SemanticStageSkip,
) {
    suspend operator fun invoke(
        scope: ScanScope,
        kinds: Set<MediaKind> = AllMediaKinds,
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): Outcome<ScanSummary> {
        if (!scope.isReachable(storageAccess.current(), scanFolders.folders.first())) {
            return Outcome.Failure(DomainError.PermissionDenied)
        }
        skip.reset()
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
                val exact = findExact(scanId, appSettings.minFileSizeBytes, kinds) { done, total ->
                    onProgress(ScanProgress(ScanStage.EXACT, done, total))
                }
                val near = if (appSettings.nearDuplicatesEnabled) {
                    onProgress(ScanProgress(ScanStage.NEAR, 0, null))
                    findNear(scanId, appSettings.nearHammingThreshold, appSettings.minFileSizeBytes, exact.groups, kinds) { done, total ->
                        onProgress(ScanProgress(ScanStage.NEAR, done, total))
                    }
                } else {
                    null
                }
                val model = appSettings.aiModelId?.let(ModelCatalog::byId)
                val semantic = if (appSettings.semanticEnabled && model != null && (!appSettings.aiOnlyWhileCharging || power.isCharging())) {
                    onProgress(ScanProgress(ScanStage.SEMANTIC, 0, null))
                    unlessSkipped {
                        findSemantic(scanId, model, appSettings.aiBackend, appSettings.semanticThreshold, appSettings.minFileSizeBytes, exact.groups, near?.groups.orEmpty(), kinds) { done, total ->
                            onProgress(ScanProgress(ScanStage.SEMANTIC, done, total))
                        }
                    }
                } else {
                    null
                }
                exact.toSummary(index, near, semantic, saveGroups(scanId, exact.groups, near?.groups.orEmpty(), semantic?.groups.orEmpty()))
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

    /** Runs [stage] until it ends or a skip is asked for; a skipped stage gives null. Vectors saved before the skip stay cached. */
    private suspend fun <T> unlessSkipped(stage: suspend () -> T?): T? = coroutineScope {
        val work = async { stage() }
        val skipped = async { skip.requested.first { it } }
        select {
            work.onAwait { result ->
                skipped.cancel()
                result
            }
            skipped.onAwait {
                work.cancel()
                null
            }
        }
    }

    /** Near and semantic groups count like the reclaim query: every copy but the largest. */
    private fun ExactScanResult.toSummary(index: IndexStats, near: NearScanResult?, semantic: NearScanResult?, kept: SavedGroups): ScanSummary {
        val sizes = near?.sizes.orEmpty() + semantic?.sizes.orEmpty()
        val similar = kept.near + kept.semantic
        return ScanSummary(
            index = index,
            exactGroups = kept.exact.size,
            duplicateFiles = kept.exact.sumOf { it.fileIds.size - 1 } + similar.sumOf { it.fileIds.size - 1 },
            reclaimableBytes = kept.exact.sumOf { it.size * (it.fileIds.size - 1) } +
                similar.sumOf { group -> group.fileIds.map { sizes.getValue(it) }.let { it.sum() - it.max() } },
            hashesComputed = partialHashed + fullHashed + (near?.hashed ?: 0) + (semantic?.hashed ?: 0),
            unreadable = unreadable + (near?.unreadable ?: 0) + (semantic?.unreadable ?: 0),
        )
    }
}
