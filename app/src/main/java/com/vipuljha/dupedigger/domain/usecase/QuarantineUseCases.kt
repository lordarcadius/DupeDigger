package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.repository.FileRemover
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import javax.inject.Inject

/** Restores each file in turn; returns how many could not be restored. */
class RestoreQuarantinedUseCase @Inject constructor(private val quarantine: QuarantineRepository) {
    suspend operator fun invoke(ids: Collection<Long>): Int =
        ids.count { quarantine.restore(it) is Outcome.Failure }
}

/** Undoes a delete: media this app trashed comes out of the trash, quarantined files go back; returns how many couldn't. */
class UndoRemovalUseCase @Inject constructor(
    private val remover: FileRemover,
    private val restoreQuarantined: RestoreQuarantinedUseCase,
) {
    suspend operator fun invoke(trashedUris: List<String>, quarantineIds: List<Long>): Int =
        remover.untrash(trashedUris) + restoreQuarantined(quarantineIds)
}

/** Deletes each file for good, the only way a file leaves Dupe Digger (D-007); returns how many could not go. */
class DeleteQuarantinedUseCase @Inject constructor(private val quarantine: QuarantineRepository) {
    suspend operator fun invoke(ids: Collection<Long>): Int =
        ids.count { quarantine.deletePermanently(it) is Outcome.Failure }
}
