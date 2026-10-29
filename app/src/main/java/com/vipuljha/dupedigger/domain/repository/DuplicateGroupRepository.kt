package com.vipuljha.dupedigger.domain.repository

import androidx.paging.PagingData
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.KindReclaim
import kotlinx.coroutines.flow.Flow

interface DuplicateGroupRepository {
    /** Reclaimable space per kind of file, over groups that still have at least two members. */
    fun observeReclaimable(): Flow<List<KindReclaim>>

    /** Matching groups with at least two members, most space to free first; refreshes when groups or keep choices change. */
    fun pagedGroups(filter: GroupFilter): Flow<PagingData<GroupWithMembers>>

    suspend fun allGroups(filter: GroupFilter): List<GroupWithMembers>

    suspend fun ignoredFingerprints(): Set<String>

    /** Replaces every saved group with [groups] for [scanId], in one transaction. */
    suspend fun replaceGroups(scanId: Long, groups: List<FoundGroup>)

    /** The member ids of every saved group that contains any of [fileIds]. */
    suspend fun groupsContaining(fileIds: Set<Long>): List<Set<Long>>

    /** The `sourceUri` the user chose to keep in the group with [fingerprint], if any. */
    suspend fun keepChoice(fingerprint: String): String?
}
