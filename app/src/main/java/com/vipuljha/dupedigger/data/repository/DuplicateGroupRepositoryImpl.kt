package com.vipuljha.dupedigger.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.dao.DuplicateGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.IgnoredGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.KeepChoiceDao
import com.vipuljha.dupedigger.data.local.db.entity.DuplicateGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.GroupMemberEntity
import com.vipuljha.dupedigger.data.local.db.entity.IgnoredGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.KeepChoiceEntity
import com.vipuljha.dupedigger.data.paging.GroupPage
import com.vipuljha.dupedigger.data.paging.GroupPagingSource
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateGroupRepositoryImpl @Inject constructor(
    private val db: DupeDiggerDatabase,
    private val groupDao: DuplicateGroupDao,
    private val ignoredDao: IgnoredGroupDao,
    private val keepChoiceDao: KeepChoiceDao,
) : DuplicateGroupRepository {

    override fun observeReclaimable(): Flow<List<KindReclaim>> = groupDao.observeReclaimable().map { rows ->
        rows.map { KindReclaim(MediaKind.valueOf(it.mediaKind), it.groupCount, it.files, it.bytes) }
    }

    override fun observeGroup(fingerprint: String): Flow<GroupWithMembers?> = groupDao.observeGroupDetail(fingerprint).map { rows ->
        val first = rows.firstOrNull() ?: return@map null
        GroupWithMembers(
            groupId = first.groupId,
            fingerprint = first.fingerprint,
            tier = MatchTier.valueOf(first.tier),
            kind = MediaKind.valueOf(rows.minOf { it.mediaKind }),
            members = rows.map { GroupMember(it.fileId, it.sourceUri, it.path, it.size, it.mtime, name = it.name) }.toImmutableList(),
            keepChoice = first.keepChoice,
        )
    }

    override fun observeDecisionChanges(): Flow<Unit> =
        db.invalidationTracker.createFlow("keep_choice", "duplicate_group", emitInitialState = false).map { }

    override suspend fun setKeepChoice(fingerprint: String, sourceUri: String) =
        keepChoiceDao.upsert(KeepChoiceEntity(fingerprint, sourceUri, chosenAt = System.currentTimeMillis()))

    override suspend fun markNotDuplicates(fingerprint: String) = db.withTransaction {
        ignoredDao.upsert(IgnoredGroupEntity(fingerprint, ignoredAt = System.currentTimeMillis()))
        groupDao.deleteByFingerprint(fingerprint)
    }

    override suspend fun ignoredFingerprints(): Set<String> = ignoredDao.fingerprints().toSet()

    override suspend fun replaceGroups(scanId: Long, groups: List<FoundGroup>) = db.withTransaction {
        groupDao.deleteAll()
        if (groups.isEmpty()) return@withTransaction
        val ids = groupDao.insertGroups(
            groups.map { DuplicateGroupEntity(tier = it.tier.name, score = it.score, scanId = scanId, fingerprint = it.fingerprint) },
        )
        groupDao.insertMembers(
            groups.zip(ids).flatMap { (group, groupId) -> group.fileIds.map { GroupMemberEntity(groupId, it, similarity = group.score) } },
        )
    }

    override suspend fun groupsContaining(fileIds: Set<Long>): List<Set<Long>> =
        fileIds.chunked(MAX_QUERY_ARGS)
            .flatMap { groupDao.membersOfGroupsContaining(it) }
            .groupBy({ it.groupId }, { it.fileId })
            .values.map { it.toSet() }

    override suspend fun keepChoice(fingerprint: String): String? = keepChoiceDao.get(fingerprint)?.keeperSourceUri

    override fun pagedGroups(filter: GroupFilter): Flow<PagingData<GroupWithMembers>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { groupPagingSource(filter) },
    ).flow

    internal fun groupPagingSource(filter: GroupFilter) = GroupPagingSource(db) { offset, limit -> loadGroups(filter, limit, offset) }

    override suspend fun allGroups(filter: GroupFilter): List<GroupWithMembers> = loadGroups(filter, ALL, 0).groups

    /** Headers and members are read in one transaction, so a scan replacing groups meanwhile can't leave a group without members. */
    private suspend fun loadGroups(filter: GroupFilter, limit: Int, offset: Int): GroupPage = db.withTransaction {
        val headers = groupDao.groupHeaders(filter.kind?.name, filter.tier?.name, limit, offset)
        val members = headers.map { it.groupId }.chunked(MAX_QUERY_ARGS)
            .flatMap { groupDao.membersOfGroups(it) }
            .groupBy { it.groupId }
        val groups = headers.mapNotNull { header ->
            val rows = members[header.groupId].orEmpty()
            if (rows.size < 2) return@mapNotNull null
            GroupWithMembers(
                groupId = header.groupId,
                fingerprint = header.fingerprint,
                tier = MatchTier.valueOf(header.tier),
                kind = MediaKind.valueOf(header.mediaKind),
                members = rows.map { GroupMember(it.fileId, it.sourceUri, it.path, it.size, it.mtime, name = it.name) }.toImmutableList(),
                keepChoice = header.keepChoice,
            )
        }
        GroupPage(groups, fetched = headers.size)
    }

    private companion object {
        const val MAX_QUERY_ARGS = 500
        const val PAGE_SIZE = 50
        const val ALL = -1
    }
}
