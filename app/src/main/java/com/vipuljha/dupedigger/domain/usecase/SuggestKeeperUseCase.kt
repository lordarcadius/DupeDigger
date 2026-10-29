package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.detector.keeperOf
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import javax.inject.Inject

/** The member to keep: the user's saved choice while that file is still in the group, otherwise the ranking's pick. */
class SuggestKeeperUseCase @Inject constructor(
    private val groups: DuplicateGroupRepository,
) {
    suspend operator fun invoke(fingerprint: String, members: List<GroupMember>): GroupMember =
        keeperOf(members, groups.keepChoice(fingerprint))
}
