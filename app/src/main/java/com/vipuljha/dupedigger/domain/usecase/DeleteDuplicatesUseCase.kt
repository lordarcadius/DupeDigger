package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.RemovalResult
import com.vipuljha.dupedigger.domain.model.TrashConsent
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.FileRemover
import javax.inject.Inject

/**
 * Removes the chosen duplicates recoverably, never the last copy (D-007).
 * - Refuses, before touching anything, a file in no saved group or a request that would empty any group it touches.
 * - Removed files leave the index, so their groups shrink; files that failed or await consent stay.
 */
class DeleteDuplicatesUseCase @Inject constructor(
    private val groups: DuplicateGroupRepository,
    private val fileIndex: FileIndexRepository,
    private val remover: FileRemover,
) {
    suspend operator fun invoke(fileIds: Set<Long>): Outcome<RemovalResult> {
        val memberSets = groups.groupsContaining(fileIds)
        if (!memberSets.flatten().toSet().containsAll(fileIds)) return Outcome.Failure(DomainError.NotFound)
        if (memberSets.any { members -> members.all { it in fileIds } }) return Outcome.Failure(DomainError.WouldRemoveAllCopies)

        val result = remover.remove(fileIndex.filesByIds(fileIds))
        fileIndex.remove(result.removedIds)
        return Outcome.Success(result)
    }
}

/** Call after the user accepts the system trash prompt; the trashed files then leave the index. */
class ConfirmTrashUseCase @Inject constructor(
    private val fileIndex: FileIndexRepository,
) {
    suspend operator fun invoke(consent: TrashConsent) = fileIndex.remove(consent.fileIds)
}
