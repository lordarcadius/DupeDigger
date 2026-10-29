package com.vipuljha.dupedigger.presentation

import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.AppSettings
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
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
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

    override fun observeReclaimable() = reclaimable
    override suspend fun ignoredFingerprints() = emptySet<String>()
    override suspend fun replaceGroups(scanId: Long, groups: List<FoundGroup>) = Unit
    override suspend fun groupsContaining(fileIds: Set<Long>) = emptyList<Set<Long>>()
    override suspend fun keepChoice(fingerprint: String): String? = null
}
