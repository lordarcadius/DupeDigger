package com.vipuljha.dupedigger.presentation

import androidx.paging.PagingData
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.AudioFile
import com.vipuljha.dupedigger.domain.model.AudioSignature
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.DocumentFile
import com.vipuljha.dupedigger.domain.model.EmbeddedFile
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.QuarantinedFile
import com.vipuljha.dupedigger.domain.model.RemovalResult
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.model.TrashConsent
import com.vipuljha.dupedigger.domain.model.VideoFile
import com.vipuljha.dupedigger.domain.model.VideoSignature
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.FileRemover
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class FakeScheduler : ScanScheduler {
    val progress = MutableStateFlow<ScanProgress?>(null)
    val started = mutableListOf<ScanScope>()
    var cancelled = false

    override fun start(scope: ScanScope) {
        started += scope
    }
    override fun cancel() {
        cancelled = true
    }
    override fun observeProgress() = progress
}

class FakeScanRuns : ScanRunRepository {
    val latest = MutableStateFlow<ScanRunInfo?>(null)

    override suspend fun start(scope: ScanScope) = 0L
    override suspend fun finish(scanId: Long, status: ScanStatus, summary: ScanSummary?) = Unit
    override fun observeLatest() = latest
}

class FakeStorageAccess(var state: StorageAccessState = StorageAccessState(MediaAccess.FULL, allFiles = true)) : StorageAccess {
    override fun current() = state
    override val mediaPermissions = emptyList<String>()
}

class FakeFolders : ScanFolderRepository {
    override val folders = MutableStateFlow<ImmutableList<ScanFolder>>(persistentListOf())

    override suspend fun add(treeUri: String): Outcome<Unit> {
        folders.update { (it + ScanFolder(treeUri, "Folder", null)).toImmutableList() }
        return Outcome.Success(Unit)
    }
    override suspend fun remove(treeUri: String) = Unit
}

class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())

    override suspend fun update(transform: (AppSettings) -> AppSettings) = settings.update(transform)
}

class FakeReclaimGroups : DuplicateGroupRepository {
    val reclaimable = MutableStateFlow<List<KindReclaim>>(emptyList())

    val groups = mutableListOf<GroupWithMembers>()
    val filters = mutableListOf<GroupFilter>()

    override fun observeReclaimable() = reclaimable
    val tierCounts = MutableStateFlow<Map<MatchTier, Int>>(emptyMap())
    override fun observeTierCounts() = tierCounts
    override fun pagedGroups(filter: GroupFilter) = flowOf(PagingData.from(groups.toList()))
    override suspend fun allGroups(filter: GroupFilter): List<GroupWithMembers> {
        filters += filter
        return groups.filter { (filter.kind == null || it.kind == filter.kind) && (filter.tier == null || it.tier == filter.tier) }
    }
    override suspend fun ignoredFingerprints() = emptySet<String>()
    override suspend fun replaceGroups(scanId: Long, groups: List<FoundGroup>) = Unit
    override suspend fun groupsContaining(fileIds: Set<Long>) =
        groups.map { g -> g.members.map { it.fileId }.toSet() }.filter { set -> set.any { it in fileIds } }
    override suspend fun keepChoice(fingerprint: String): String? = null

    val detail = MutableStateFlow<GroupWithMembers?>(null)
    val decisions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val keepChoices = mutableMapOf<String, String>()
    val notDuplicates = mutableListOf<String>()

    override fun observeGroup(fingerprint: String) = detail
    override fun observeDecisionChanges() = decisions
    override suspend fun setKeepChoice(fingerprint: String, sourceUri: String) {
        keepChoices[fingerprint] = sourceUri
        detail.update { it?.copy(keepChoice = sourceUri) }
    }
    override suspend fun markNotDuplicates(fingerprint: String) {
        notDuplicates += fingerprint
        detail.value = null
    }
}

open class FakeFileIndex(initial: Set<Long>) : FileIndexRepository {
    val ids = initial.toMutableSet()

    override suspend fun imagesSeenIn(scanId: Long) = emptyList<ImageFile>()
    override suspend fun saveImageHashes(hashes: Map<Long, ImageHashes>) = Unit
    override suspend fun videosSeenIn(scanId: Long) = emptyList<VideoFile>()
    override suspend fun saveVideoSignatures(signatures: Map<Long, VideoSignature>) = Unit
    override suspend fun documentsSeenIn(scanId: Long) = emptyList<DocumentFile>()
    override suspend fun saveDocumentHashes(hashes: Map<Long, Long>) = Unit
    override suspend fun audioSeenIn(scanId: Long) = emptyList<AudioFile>()
    override suspend fun saveAudioSignatures(signatures: Map<Long, AudioSignature>) = Unit
    override suspend fun embeddingCandidates(scanId: Long, modelKey: String, kinds: Set<MediaKind>) = emptyList<EmbeddedFile>()
    override suspend fun saveEmbeddings(modelKey: String, vectors: Map<Long, FloatArray>) = Unit
    override suspend fun filesByIds(ids: Collection<Long>) =
        ids.filter { it in this.ids }.map { StoredFile(it, "file:/f$it", "/f$it", "f$it", MediaKind.OTHER, 100) }
    override suspend fun remove(ids: Collection<Long>) {
        this.ids -= ids.toSet()
    }
    override suspend fun record(files: List<DiscoveredFile>, scanId: Long) = IndexStats()
    override suspend fun removeNotSeen(scope: ScanScope, scanId: Long) = 0
    override suspend fun filesSeenIn(scanId: Long) = emptyList<IndexedFile>()
    override suspend fun saveHashes(files: List<IndexedFile>) = Unit
}

/**
 * Removes every file except those in [needConsent], which await the trash prompt.
 * - Files in [trashable] are trashed as `media:<id>`; the rest are quarantined as id + 100.
 */
class FakeRemover : FileRemover {
    val needConsent = mutableSetOf<Long>()
    val trashable = mutableSetOf<Long>()
    val untrashed = mutableListOf<String>()

    override suspend fun remove(files: List<StoredFile>): RemovalResult {
        val ids = files.map { it.id }
        val removed = ids.filter { it !in needConsent }
        val consentIds = ids.filter { it in needConsent }
        return RemovalResult(
            removedIds = removed.toImmutableList(),
            trashedUris = removed.filter { it in trashable }.map { "media:$it" }.toImmutableList(),
            quarantineIds = removed.filterNot { it in trashable }.map { it + 100 }.toImmutableList(),
            consent = consentIds.takeIf { it.isNotEmpty() }?.let { TrashConsent(it.toImmutableList(), persistentListOf(), platformRequest = "prompt") },
            failedIds = persistentListOf(),
        )
    }

    override suspend fun untrash(mediaUris: List<String>): Int {
        untrashed += mediaUris
        return 0
    }
}

class FakeQuarantine : QuarantineRepository {
    val files = MutableStateFlow<List<QuarantinedFile>>(emptyList())
    val restored = mutableListOf<Long>()
    val failing = mutableSetOf<Long>()

    override fun observeAll() = files
    override suspend fun restore(id: Long): Outcome<Unit> {
        if (id in failing) return Outcome.Failure(DomainError.Io)
        restored += id
        files.update { list -> list.filterNot { it.id == id } }
        return Outcome.Success(Unit)
    }
    override suspend fun deletePermanently(id: Long): Outcome<Unit> {
        if (id in failing) return Outcome.Failure(DomainError.Io)
        files.update { list -> list.filterNot { it.id == id } }
        return Outcome.Success(Unit)
    }
}
