package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.BuiltInPreset
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.ScanPresetRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetUseCasesTest {
    private val repository = FakePresets()
    private val images = ScanPreset(0, "  Images Only ", ScanScope.WholeDevice, persistentSetOf(MediaKind.IMAGE))

    @Test
    fun `the user's presets come first, then every built-in`() = runTest {
        val id = (SaveScanPresetUseCase(repository)(images) as Outcome.Success).data

        val presets = ObserveScanPresetsUseCase(repository)().first()

        assertEquals(listOf(id) + BuiltInPreset.presets.map { it.id }, presets.map { it.id })
        assertEquals("Images Only", presets.first().name)
    }

    @Test
    fun `saving an edited preset replaces it`() = runTest {
        val id = (SaveScanPresetUseCase(repository)(images) as Outcome.Success).data

        SaveScanPresetUseCase(repository)(images.copy(id = id, name = "Photos", kinds = persistentSetOf(MediaKind.IMAGE, MediaKind.VIDEO)))

        val saved = repository.rows.value.single()
        assertEquals("Photos", saved.name)
        assertEquals(setOf(MediaKind.IMAGE, MediaKind.VIDEO), saved.kinds)
    }

    @Test
    fun `a preset needs a name and something to scan`() = runTest {
        val save = SaveScanPresetUseCase(repository)
        assertEquals(DomainError.InvalidPreset, (save(images.copy(name = "  ")) as Outcome.Failure).error)
        assertEquals(DomainError.InvalidPreset, (save(images.copy(scope = ScanScope.Folders(persistentListOf()))) as Outcome.Failure).error)
        assertEquals(DomainError.InvalidPreset, (save(images.copy(scope = ScanScope.Paths(persistentListOf("..", "/")))) as Outcome.Failure).error)
        val folder = ScanFolder("content://tree/camera", "Camera", null)
        assertEquals(Outcome.Success(1L), save(images.copy(scope = ScanScope.Folders(persistentListOf(folder)))))
    }

    @Test
    fun `built-in presets can't be changed or deleted`() = runTest {
        val whatsapp = BuiltInPreset.WHATSAPP.toPreset()

        assertEquals(DomainError.BuiltInPreset, (SaveScanPresetUseCase(repository)(whatsapp.copy(name = "Mine")) as Outcome.Failure).error)
        assertEquals(DomainError.BuiltInPreset, (DeleteScanPresetUseCase(repository)(whatsapp) as Outcome.Failure).error)
        assertEquals(emptyList<Long>(), repository.deleted)
    }

    @Test
    fun `a user's preset can be deleted`() = runTest {
        val id = (SaveScanPresetUseCase(repository)(images) as Outcome.Success).data

        val outcome = DeleteScanPresetUseCase(repository)(repository.rows.value.single())

        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals(listOf(id), repository.deleted)
        assertEquals(emptyList<ScanPreset>(), repository.rows.value)
    }

    private class FakePresets : ScanPresetRepository {
        val rows = MutableStateFlow(emptyList<ScanPreset>())
        val deleted = mutableListOf<Long>()
        private var nextId = 1L

        override val presets = rows.map { it.toImmutableList() as ImmutableList<ScanPreset> }

        override suspend fun save(preset: ScanPreset): Long {
            if (preset.id == 0L) {
                val id = nextId++
                rows.value += preset.copy(id = id)
                return id
            }
            rows.value = rows.value.map { if (it.id == preset.id) preset else it }
            return preset.id
        }

        override suspend fun delete(id: Long) {
            deleted += id
            rows.value = rows.value.filterNot { it.id == id }
        }
    }
}
