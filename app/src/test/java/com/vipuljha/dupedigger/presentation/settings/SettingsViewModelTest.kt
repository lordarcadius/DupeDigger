package com.vipuljha.dupedigger.presentation.settings

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.usecase.AddScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.ExcludeFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.RemoveExcludedPathUseCase
import com.vipuljha.dupedigger.domain.usecase.RemoveScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.SetNearThresholdUseCase
import com.vipuljha.dupedigger.presentation.FakeSettings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeFolders : ScanFolderRepository {
        val flow = MutableStateFlow<ImmutableList<ScanFolder>>(persistentListOf())
        var addResult: Outcome<Unit> = Outcome.Success(Unit)
        override val folders = flow
        override suspend fun add(treeUri: String): Outcome<Unit> {
            if (addResult is Outcome.Success) flow.update { (it + ScanFolder(treeUri, "Picked", "/storage/emulated/0/Picked")).toImmutableList() }
            return addResult
        }
        override suspend fun remove(treeUri: String) = flow.update { list -> list.filterNot { it.uri == treeUri }.toImmutableList() }
    }

    private class FakeExcluded : ExcludedPathRepository {
        val flow = MutableStateFlow<ImmutableList<String>>(persistentListOf())
        override val paths = flow
        override suspend fun excludeTree(treeUri: String): Outcome<Unit> =
            if (treeUri.startsWith("content://com.android.externalstorage")) {
                flow.update { (it + "/storage/emulated/0/Private").toImmutableList() }
                Outcome.Success(Unit)
            } else {
                Outcome.Failure(DomainError.NotFound)
            }
        override suspend fun remove(path: String) = flow.update { list -> list.filterNot { it == path }.toImmutableList() }
    }

    private val folders = FakeFolders()
    private val excluded = FakeExcluded()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val settings = FakeSettings()

    private fun viewModel() = SettingsViewModel(
        folders, excluded,
        AddScanFolderUseCase(folders), RemoveScanFolderUseCase(folders),
        ExcludeFolderUseCase(excluded), RemoveExcludedPathUseCase(excluded),
        settings, SetNearThresholdUseCase(settings),
    )

    @Test
    fun `the similar-files threshold shows the setting and saves within the offered range`() {
        val vm = viewModel()
        assertEquals(10, vm.state.value.nearThreshold)

        vm.onIntent(SettingsIntent.NearThresholdChanged(7))
        assertEquals(7, settings.settings.value.nearHammingThreshold)
        assertEquals(7, vm.state.value.nearThreshold)

        vm.onIntent(SettingsIntent.NearThresholdChanged(40))
        assertEquals(12, settings.settings.value.nearHammingThreshold)
    }

    @Test
    fun `picking a scan folder adds it and removing it drops it`() {
        val vm = viewModel()
        assertFalse(vm.state.value.isLoading)

        vm.onIntent(SettingsIntent.FolderPicked(FolderPurpose.Scan, "content://tree/1"))
        assertEquals(listOf(FolderUiModel("content://tree/1", "Picked", "/storage/emulated/0/Picked")), vm.state.value.folders)

        vm.onIntent(SettingsIntent.RemoveClicked(FolderPurpose.Scan, "content://tree/1"))
        assertEquals(emptyList<FolderUiModel>(), vm.state.value.folders)
    }

    @Test
    fun `backing out of the picker changes nothing`() {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.FolderPicked(FolderPurpose.Scan, null))
        assertEquals(emptyList<FolderUiModel>(), vm.state.value.folders)
    }

    @Test
    fun `a folder that can't be persisted shows a message`() = runTest {
        folders.addResult = Outcome.Failure(DomainError.PermissionDenied)
        val vm = viewModel()
        vm.onIntent(SettingsIntent.FolderPicked(FolderPurpose.Scan, "content://tree/1"))
        assertEquals(SettingsEffect.ShowMessage(UiText.res(R.string.settings_folder_add_failed)), vm.effect.first())
    }

    @Test
    fun `excluded folders are listed by name and can be removed`() {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.FolderPicked(FolderPurpose.Exclude, "content://com.android.externalstorage.documents/tree/primary%3APrivate"))
        assertEquals(
            listOf(FolderUiModel("/storage/emulated/0/Private", "Private", "/storage/emulated/0/Private")),
            vm.state.value.excluded,
        )

        vm.onIntent(SettingsIntent.RemoveClicked(FolderPurpose.Exclude, "/storage/emulated/0/Private"))
        assertEquals(emptyList<FolderUiModel>(), vm.state.value.excluded)
    }

    @Test
    fun `excluding a folder outside local storage shows a message`() = runTest {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.FolderPicked(FolderPurpose.Exclude, "content://com.google.drive/tree/x"))
        assertEquals(SettingsEffect.ShowMessage(UiText.res(R.string.settings_exclude_failed)), vm.effect.first())
    }

    @Test
    fun `add buttons open the picker for their purpose`() = runTest {
        val vm = viewModel()
        vm.onIntent(SettingsIntent.AddFolderClicked(FolderPurpose.Exclude))
        assertEquals(SettingsEffect.PickFolder(FolderPurpose.Exclude), vm.effect.first())
    }
}
