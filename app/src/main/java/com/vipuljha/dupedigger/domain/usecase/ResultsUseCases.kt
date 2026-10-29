package com.vipuljha.dupedigger.domain.usecase

import androidx.paging.PagingData
import androidx.paging.map
import com.vipuljha.dupedigger.domain.detector.toItem
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The saved groups that match [GroupFilter], most space to free first, each split into keeper and removable files. */
class GetDuplicateGroupsUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    operator fun invoke(filter: GroupFilter): Flow<PagingData<DuplicateGroupItem>> =
        groups.pagedGroups(filter).map { page -> page.map { it.toItem() } }
}

/**
 * Every file that could go in the matching groups: all members except each group's keeper.
 * A file that keeps another group (an exact copy that also stands in a near group) is never picked, so the
 * selection can't empty a group.
 */
class SelectAllButKeepersUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    suspend operator fun invoke(filter: GroupFilter): List<GroupMember> {
        val items = groups.allGroups(filter).map { it.toItem() }
        val keepers = items.map { it.keeper.fileId }.toSet()
        return items.flatMap { it.removable }.filter { it.fileId !in keepers }.distinctBy { it.fileId }
    }
}
