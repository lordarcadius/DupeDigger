package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.availableScopes
import com.vipuljha.dupedigger.domain.model.isReachable
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The scopes the app can read right now, widest first; read again whenever access may have changed. */
class GetAvailableScopesUseCase @Inject constructor(
    private val storageAccess: StorageAccess,
    private val scanFolders: ScanFolderRepository,
) {
    suspend operator fun invoke(): ImmutableList<ScanScope> = availableScopes(storageAccess.current(), scanFolders.folders.first())
}

/** Queues a background scan of a readable scope; a scan already queued or running is left as it is. */
class StartScanUseCase @Inject constructor(
    private val storageAccess: StorageAccess,
    private val scanFolders: ScanFolderRepository,
    private val scheduler: ScanScheduler,
) {
    suspend operator fun invoke(scope: ScanScope): Outcome<Unit> {
        if (!scope.isReachable(storageAccess.current(), scanFolders.folders.first())) return Outcome.Failure(DomainError.PermissionDenied)
        scheduler.start(scope)
        return Outcome.Success(Unit)
    }
}

class CancelScanUseCase @Inject constructor(private val scheduler: ScanScheduler) {
    operator fun invoke() = scheduler.cancel()
}

class SetNearDuplicatesEnabledUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = settings.update { it.copy(nearDuplicatesEnabled = enabled) }
}
