package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.BuiltInPreset
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.roots
import com.vipuljha.dupedigger.domain.repository.ScanPresetRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Every preset to offer: the user's own, oldest first, then the built-in ones. */
class ObserveScanPresetsUseCase @Inject constructor(private val presets: ScanPresetRepository) {
    operator fun invoke(): Flow<ImmutableList<ScanPreset>> = presets.presets.map { (it + BuiltInPreset.presets).toImmutableList() }
}

/**
 * Saves a new preset (id 0) or an edited one, with its name trimmed; returns its id.
 * - A built-in preset can't be changed.
 * - A preset needs a name and something to scan: at least one picked folder or usable path.
 */
class SaveScanPresetUseCase @Inject constructor(private val presets: ScanPresetRepository) {
    suspend operator fun invoke(preset: ScanPreset): Outcome<Long> {
        if (preset.builtIn != null) return Outcome.Failure(DomainError.BuiltInPreset)
        val name = preset.name.trim()
        val scansSomething = when (val scope = preset.scope) {
            is ScanScope.Folders -> scope.folders.isNotEmpty()
            is ScanScope.Paths -> scope.roots().isNotEmpty()
            ScanScope.WholeDevice, ScanScope.MediaOnly -> true
        }
        if (name.isEmpty() || !scansSomething) return Outcome.Failure(DomainError.InvalidPreset)
        return Outcome.Success(presets.save(preset.copy(name = name)))
    }
}

/** Deletes one of the user's presets; built-in presets can't be deleted. */
class DeleteScanPresetUseCase @Inject constructor(private val presets: ScanPresetRepository) {
    suspend operator fun invoke(preset: ScanPreset): Outcome<Unit> {
        if (!preset.deletable) return Outcome.Failure(DomainError.BuiltInPreset)
        presets.delete(preset.id)
        return Outcome.Success(Unit)
    }
}
