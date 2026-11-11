package com.vipuljha.dupedigger.domain.usecase

import androidx.paging.PagingData
import androidx.paging.map
import com.vipuljha.dupedigger.domain.detector.keeperFor
import com.vipuljha.dupedigger.domain.detector.toItem
import com.vipuljha.dupedigger.domain.model.DuplicateFolder
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.KeepRule
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The saved groups that match [GroupFilter], most space to free first, each split into keeper and removable files. */
class GetDuplicateGroupsUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    operator fun invoke(filter: GroupFilter): Flow<PagingData<DuplicateGroupItem>> =
        groups.pagedGroups(filter).map { page -> page.map { it.toItem() } }
}

/** A file a bulk selection picked, with the tier of the group it came from so the UI can say what it is deleting. */
data class SelectableFile(val fileId: Long, val size: Long, val tier: MatchTier)

/**
 * Every file that could go in the groups the filter shows: all members except the one [rule] keeps in each group.
 * - It covers every tier the filter shows, so "Select all" means what it says; the delete confirmation names how
 *   many of the picked files are only similar rather than byte-identical (D-012).
 * - A file that keeps another group (an exact copy that also stands in a near group) is never picked, so the
 *   selection can't empty a group.
 */
class SelectAllButKeepersUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    suspend operator fun invoke(
        filter: GroupFilter,
        rule: KeepRule = KeepRule.Best
    ): List<SelectableFile> {
        val matching = groups.allGroups(filter)
        val keepers = matching.associate { it.groupId to it.keeperFor(rule).fileId }
        val kept = keepers.values.toSet()
        return matching.flatMap { group ->
            group.members.filter { it.fileId != keepers.getValue(group.groupId) }
                .map { SelectableFile(it.fileId, it.size, group.tier) }
        }
            .filter { it.fileId !in kept }
            .distinctBy { it.fileId }
    }
}

/** The folders the files a selection could pick sit in, most files first, for keeping one folder's copies. */
class GetDuplicateFoldersUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    suspend operator fun invoke(filter: GroupFilter): List<DuplicateFolder> =
        groups.allGroups(filter).flatMap { it.members }.distinctBy { it.fileId }
            .mapNotNull { member ->
                member.path?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }
            }
            .groupingBy { it }.eachCount()
            .map { (path, files) -> DuplicateFolder(path, files) }
            .sortedWith(compareByDescending<DuplicateFolder> { it.files }.thenBy { it.path })
}
