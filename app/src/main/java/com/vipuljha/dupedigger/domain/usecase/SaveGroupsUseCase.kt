package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.NearGroup
import com.vipuljha.dupedigger.domain.model.exactFingerprint
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import javax.inject.Inject

data class SavedGroups(val exact: List<ExactGroup>, val near: List<NearGroup>)

/** Saves a scan's groups of every tier in place of the previous results, leaving out groups the user marked "not duplicates"; returns those kept. */
class SaveGroupsUseCase @Inject constructor(
    private val groups: DuplicateGroupRepository,
) {
    suspend operator fun invoke(scanId: Long, exactGroups: List<ExactGroup>, nearGroups: List<NearGroup> = emptyList()): SavedGroups {
        val ignored = groups.ignoredFingerprints()
        val exact = exactGroups.filter { it.fileIds.size > 1 && exactFingerprint(it.size, it.fullHash) !in ignored }
        val near = nearGroups.filter { it.fileIds.size > 1 && it.fingerprint !in ignored }
        groups.replaceGroups(
            scanId,
            exact.map { FoundGroup(exactFingerprint(it.size, it.fullHash), MatchTier.EXACT, score = 1f, fileIds = it.fileIds) } +
                near.map { FoundGroup(it.fingerprint, MatchTier.NEAR, it.score, it.fileIds) },
        )
        return SavedGroups(exact, near)
    }
}
