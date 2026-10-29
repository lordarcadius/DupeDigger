package com.vipuljha.dupedigger.data.repository

import androidx.room.withTransaction
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.dao.DuplicateGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.IgnoredGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.KeepChoiceDao
import com.vipuljha.dupedigger.data.local.db.entity.DuplicateGroupEntity
import com.vipuljha.dupedigger.data.local.db.entity.GroupMemberEntity
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateGroupRepositoryImpl @Inject constructor(
    private val db: DupeDiggerDatabase,
    private val groupDao: DuplicateGroupDao,
    private val ignoredDao: IgnoredGroupDao,
    private val keepChoiceDao: KeepChoiceDao,
) : DuplicateGroupRepository {

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

    private companion object {
        const val MAX_QUERY_ARGS = 500
    }
}
