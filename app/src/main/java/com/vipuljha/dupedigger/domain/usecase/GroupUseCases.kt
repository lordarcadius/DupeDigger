package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.detector.toItem
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.FileInspector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The group with this fingerprint, split into keeper and removable files; null once it has fewer than two members. */
class ObserveGroupUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    operator fun invoke(fingerprint: String): Flow<DuplicateGroupItem?> =
        groups.observeGroup(fingerprint).map { group -> group?.takeIf { it.members.size >= 2 }?.toItem() }
}

/** Saves the file to keep for this content; it holds across rescans because it is keyed by fingerprint. */
class ChooseKeeperUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    suspend operator fun invoke(fingerprint: String, member: GroupMember) = groups.setKeepChoice(fingerprint, member.sourceUri)
}

/** Remembers that this content isn't a duplicate and removes its saved group, in one transaction. */
class MarkNotDuplicatesUseCase @Inject constructor(private val groups: DuplicateGroupRepository) {
    suspend operator fun invoke(fingerprint: String) = groups.markNotDuplicates(fingerprint)
}

class ReadMediaInfoUseCase @Inject constructor(private val inspector: FileInspector) {
    suspend operator fun invoke(member: GroupMember, kind: MediaKind) = inspector.mediaInfo(member, kind)
}

class GetViewTargetUseCase @Inject constructor(private val inspector: FileInspector) {
    suspend operator fun invoke(member: GroupMember) = inspector.viewTarget(member)
}
