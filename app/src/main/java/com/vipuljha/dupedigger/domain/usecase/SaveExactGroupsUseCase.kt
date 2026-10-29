package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.exactFingerprint
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import javax.inject.Inject

/** Saves a scan's exact groups in place of the previous results, leaving out groups the user marked "not duplicates"; returns those kept. */
class SaveExactGroupsUseCase @Inject constructor(
    private val groups: DuplicateGroupRepository,
) {
    suspend operator fun invoke(scanId: Long, exactGroups: List<ExactGroup>): List<ExactGroup> {
        val ignored = groups.ignoredFingerprints()
        val kept = exactGroups.filter { it.fileIds.size > 1 && exactFingerprint(it.size, it.fullHash) !in ignored }
        groups.replaceGroups(
            scanId,
            kept.map { FoundGroup(exactFingerprint(it.size, it.fullHash), MatchTier.EXACT, score = 1f, fileIds = it.fileIds) },
        )
        return kept
    }
}
