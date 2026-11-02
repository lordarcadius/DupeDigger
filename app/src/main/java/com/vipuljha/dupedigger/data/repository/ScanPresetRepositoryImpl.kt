package com.vipuljha.dupedigger.data.repository

import com.vipuljha.dupedigger.data.local.db.dao.ScanPresetDao
import com.vipuljha.dupedigger.data.local.db.entity.ScanPresetEntity
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScopeKind
import com.vipuljha.dupedigger.domain.model.kind
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanPresetRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanPresetRepositoryImpl @Inject constructor(
    private val dao: ScanPresetDao,
    scanFolders: ScanFolderRepository,
) : ScanPresetRepository {

    override val presets: Flow<ImmutableList<ScanPreset>> =
        combine(dao.observeAll(), scanFolders.folders) { rows, picked -> rows.mapNotNull { it.toDomain(picked) }.toImmutableList() }

    override suspend fun save(preset: ScanPreset): Long {
        val row = preset.toEntity(createdAt = System.currentTimeMillis())
        if (preset.id == 0L) return dao.insert(row)
        dao.update(row.id, row.name, row.scopeKind, row.locations, row.mediaKinds)
        return preset.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

internal fun ScanPreset.toEntity(createdAt: Long) = ScanPresetEntity(
    id = id,
    name = name,
    scopeKind = scope.kind.name,
    locations = when (val scope = scope) {
        is ScanScope.Folders -> scope.folders.joinToString("\n") { it.uri }
        is ScanScope.Paths -> scope.paths.joinToString("\n")
        ScanScope.WholeDevice, ScanScope.MediaOnly -> ""
    },
    mediaKinds = kinds.joinToString(",") { it.name },
    createdAt = createdAt,
)

/**
 * Null for a scope kind this build doesn't know.
 * - A stored folder is kept only while its grant is among [picked].
 * - Unknown file types are dropped; a preset left with none searches every type.
 */
internal fun ScanPresetEntity.toDomain(picked: List<ScanFolder>): ScanPreset? {
    val stored = locations.split('\n').filter { it.isNotEmpty() }
    val scope = when (ScopeKind.entries.firstOrNull { it.name == scopeKind }) {
        ScopeKind.WHOLE_DEVICE -> ScanScope.WholeDevice
        ScopeKind.MEDIA_ONLY -> ScanScope.MediaOnly
        ScopeKind.FOLDERS -> ScanScope.Folders(picked.filter { it.uri in stored }.toImmutableList())
        ScopeKind.PATHS -> ScanScope.Paths(stored.toImmutableList())
        null -> return null
    }
    val kinds = mediaKinds.split(',')
        .mapNotNull { name -> MediaKind.entries.firstOrNull { it.name == name } }
        .toImmutableSet()
        .ifEmpty { AllMediaKinds }
    return ScanPreset(id, name, scope, kinds)
}
