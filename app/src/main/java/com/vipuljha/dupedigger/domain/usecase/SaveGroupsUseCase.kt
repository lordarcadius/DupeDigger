package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.NearGroup
import com.vipuljha.dupedigger.domain.model.exactFingerprint
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.FilePresence
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

data class SavedGroups(val exact: List<ExactGroup>, val near: List<NearGroup>, val semantic: List<NearGroup> = emptyList())

/**
 * Saves a scan's groups of every tier in place of the previous results; returns those kept.
 * - Groups the user marked "not duplicates" are left out.
 * - A member this scan's scope didn't cover is only as fresh as the scan that indexed it, so it is checked against
 *   the device first. One that has since gone loses its index row; an exact group drops it and keeps the rest,
 *   while a near or semantic group goes altogether, because its fingerprint is made of its members.
 */
class SaveGroupsUseCase @Inject constructor(
    private val groups: DuplicateGroupRepository,
    private val fileIndex: FileIndexRepository,
    private val presence: FilePresence,
) {
    suspend operator fun invoke(
        scanId: Long,
        exactGroups: List<ExactGroup>,
        nearGroups: List<NearGroup> = emptyList(),
        semanticGroups: List<NearGroup> = emptyList(),
    ): SavedGroups {
        val gone = dropVanished(scanId, exactGroups.flatMap { it.fileIds } + (nearGroups + semanticGroups).flatMap { it.fileIds })
        val ignored = groups.ignoredFingerprints()
        val exact = exactGroups.map { it.without(gone) }
            .filter { it.fileIds.size > 1 && exactFingerprint(it.size, it.fullHash) !in ignored }
        val near = nearGroups.kept(gone, ignored)
        val semantic = semanticGroups.kept(gone, ignored)
        groups.replaceGroups(
            scanId,
            exact.map { FoundGroup(exactFingerprint(it.size, it.fullHash), MatchTier.EXACT, score = 1f, fileIds = it.fileIds) } +
                near.map { FoundGroup(it.fingerprint, MatchTier.NEAR, it.score, it.fileIds) } +
                semantic.map { FoundGroup(it.fingerprint, MatchTier.SEMANTIC, it.score, it.fileIds) },
        )
        return SavedGroups(exact, near, semantic)
    }

    /** Members an earlier scan indexed that are no longer on the device; their index rows go with them. */
    private suspend fun dropVanished(scanId: Long, memberIds: List<Long>): Set<Long> {
        if (memberIds.isEmpty()) return emptySet()
        val carried = fileIndex.notSeenIn(scanId, memberIds.toSet())
        if (carried.isEmpty()) return emptySet()
        val files = fileIndex.filesByIds(carried)
        val missingUris = presence.missing(files.map { it.sourceUri })
        val gone = files.filter { it.sourceUri in missingUris }.mapTo(HashSet()) { it.id }
        if (gone.isNotEmpty()) fileIndex.remove(gone)
        return gone
    }

    private fun ExactGroup.without(gone: Set<Long>) =
        if (gone.isEmpty()) this else copy(fileIds = fileIds.filterNot { it in gone }.toImmutableList())

    private fun List<NearGroup>.kept(gone: Set<Long>, ignored: Set<String>) =
        filter { it.fileIds.size > 1 && it.fingerprint !in ignored && it.fileIds.none { id -> id in gone } }
}
