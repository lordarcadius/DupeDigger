package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.repository.FileRemover
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import javax.inject.Inject

/** Restores each file in turn; returns how many could not be restored. */
class RestoreQuarantinedUseCase @Inject constructor(private val quarantine: QuarantineRepository) {
    suspend operator fun invoke(ids: Collection<Long>): Int = ids.count { quarantine.restore(it) is Outcome.Failure }
}

/** The only way a file leaves Dupe Digger for good (D-007). */
class DeleteQuarantinedUseCase @Inject constructor(private val quarantine: QuarantineRepository) {
    suspend operator fun invoke(id: Long) = quarantine.deletePermanently(id)
}

/** Undoes a delete: media this app trashed comes out of the trash, quarantined files go back; returns how many couldn't. */
class UndoRemovalUseCase @Inject constructor(
    private val remover: FileRemover,
    private val restoreQuarantined: RestoreQuarantinedUseCase,
) {
    suspend operator fun invoke(trashedUris: List<String>, quarantineIds: List<Long>): Int =
        remover.untrash(trashedUris) + restoreQuarantined(quarantineIds)
}
