package com.vipuljha.dupedigger.presentation.scan

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.data.worker.InMemorySemanticStageSkip
import com.vipuljha.dupedigger.domain.model.BuiltInPreset
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.model.ScopeKind
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.usecase.AddScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.CancelScanUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteScanPresetUseCase
import com.vipuljha.dupedigger.domain.usecase.GetAvailableScopesUseCase
import com.vipuljha.dupedigger.domain.usecase.ObserveScanPresetsUseCase
import com.vipuljha.dupedigger.domain.usecase.SaveScanPresetUseCase
import com.vipuljha.dupedigger.domain.usecase.SetNearDuplicatesEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SkipSemanticStageUseCase
import com.vipuljha.dupedigger.domain.usecase.StartScanUseCase
import com.vipuljha.dupedigger.presentation.FakeFolders
import com.vipuljha.dupedigger.presentation.FakePresets
import com.vipuljha.dupedigger.presentation.FakeScanRuns
import com.vipuljha.dupedigger.presentation.FakeScheduler
import com.vipuljha.dupedigger.presentation.FakeSettings
import com.vipuljha.dupedigger.presentation.FakeStorageAccess
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScanViewModelTest {
    private val settings = FakeSettings()
    private val runs = FakeScanRuns()
    private val scheduler = FakeScheduler()
    private val access = FakeStorageAccess()
    private val folders = FakeFolders()
    private val presets = FakePresets()
    private val skip = InMemorySemanticStageSkip()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ScanViewModel(
        settings, runs, scheduler,
        ObserveScanPresetsUseCase(presets),
        GetAvailableScopesUseCase(access, folders),
        StartScanUseCase(access, folders, scheduler),
        CancelScanUseCase(scheduler),
        SetNearDuplicatesEnabledUseCase(settings),
        SetSemanticEnabledUseCase(settings),
        SaveScanPresetUseCase(presets),
        DeleteScanPresetUseCase(presets),
        AddScanFolderUseCase(folders),
        SkipSemanticStageUseCase(skip),
        skip,
    )

    /** No preset, every file type, then the where step. */
    private fun ScanViewModel.toWhere() {
        onIntent(ScanIntent.NextClicked)
        onIntent(ScanIntent.NextClicked)
    }

    private fun completed(id: Long, duplicates: Int) = ScanRunInfo(
        id, ScopeKind.WHOLE_DEVICE, ScanStatus.COMPLETED, 0, 1,
        ScanSummary(IndexStats(), exactGroups = 1, duplicateFiles = duplicates, reclaimableBytes = 5_000, hashesComputed = 0, unreadable = 0),
    )

    @Test
    fun `setup offers the readable scopes and picked folders, and picks the widest`() {
        val state = viewModel().state.value

        assertEquals(ScanPhase.Setup, state.phase)
        assertEquals(listOf(ScopeKind.WHOLE_DEVICE, ScopeKind.MEDIA_ONLY, ScopeKind.FOLDERS), state.scopes.map { it.kind })
        assertEquals(ScopeKind.WHOLE_DEVICE, state.selectedScope)
    }

    @Test
    fun `tapping scan shows presets first, then file types, then where to look`() = runTest {
        presets.save(ScanPreset(0, "Images Only", ScanScope.WholeDevice, persistentSetOf(MediaKind.IMAGE)))
        val vm = viewModel()

        assertEquals(SetupStep.Presets, vm.state.value.step)
        assertEquals(listOf(1L) + BuiltInPreset.presets.map { it.id }, vm.state.value.presets.map { it.id })
        assertNull(vm.state.value.selectedPresetId)

        vm.onIntent(ScanIntent.NextClicked)
        assertEquals(SetupStep.Types, vm.state.value.step)
        assertTrue(vm.state.value.kinds.all { it.selected })
        MediaKind.entries.forEach { vm.onIntent(ScanIntent.KindToggled(it)) }
        vm.onIntent(ScanIntent.NextClicked)
        assertEquals(SetupStep.Types, vm.state.value.step)

        vm.onIntent(ScanIntent.KindToggled(MediaKind.VIDEO))
        vm.onIntent(ScanIntent.NextClicked)
        assertEquals(SetupStep.Where, vm.state.value.step)
        vm.onIntent(ScanIntent.ScopeSelected(ScopeKind.MEDIA_ONLY))
        vm.onIntent(ScanIntent.StartClicked)

        assertEquals(listOf<ScanScope>(ScanScope.MediaOnly), scheduler.started)
        assertEquals(listOf(setOf(MediaKind.VIDEO)), scheduler.startedKinds)
    }

    @Test
    fun `back goes one step back`() {
        val vm = viewModel()
        vm.toWhere()

        vm.onIntent(ScanIntent.BackClicked)
        assertEquals(SetupStep.Types, vm.state.value.step)
        vm.onIntent(ScanIntent.BackClicked)
        assertEquals(SetupStep.Presets, vm.state.value.step)
    }

    @Test
    fun `a preset starts with its own scope and file types`() {
        val vm = viewModel()

        vm.onIntent(ScanIntent.PresetSelected(BuiltInPreset.CAMERA.toPreset().id))
        vm.onIntent(ScanIntent.StartClicked)

        assertEquals(listOf<ScanScope>(ScanScope.Paths(persistentListOf("DCIM/Camera"))), scheduler.started)
        assertEquals(listOf(setOf(MediaKind.IMAGE, MediaKind.VIDEO)), scheduler.startedKinds)
    }

    @Test
    fun `built-in presets need all files access and can't be edited or deleted`() {
        access.state = StorageAccessState(MediaAccess.FULL, allFiles = false)
        val vm = viewModel()
        val camera = vm.state.value.presets.single { it.id == BuiltInPreset.CAMERA.toPreset().id }

        assertFalse(camera.enabled)
        assertFalse(camera.editable)
        assertEquals(UiText.res(R.string.home_hint_all_files), camera.hint)
        vm.onIntent(ScanIntent.EditPresetClicked(camera.id))
        vm.onIntent(ScanIntent.DeletePresetClicked(camera.id))
        assertNull(vm.state.value.draft)
        assertNull(vm.state.value.confirmDelete)
    }

    @Test
    fun `a new preset is built with the same steps and saved`() {
        val vm = viewModel()

        vm.onIntent(ScanIntent.NewPresetClicked)
        assertEquals(SetupStep.Types, vm.state.value.step)
        MediaKind.entries.filter { it != MediaKind.IMAGE }.forEach { vm.onIntent(ScanIntent.KindToggled(it)) }
        vm.onIntent(ScanIntent.NextClicked)
        assertFalse(vm.state.value.canFinish)
        vm.onIntent(ScanIntent.DraftNameChanged("Images Only"))
        vm.onIntent(ScanIntent.SavePresetClicked)

        val saved = presets.rows.value.single()
        assertEquals("Images Only", saved.name)
        assertEquals(ScanScope.WholeDevice, saved.scope)
        assertEquals(setOf(MediaKind.IMAGE), saved.kinds)
        assertEquals(SetupStep.Presets, vm.state.value.step)
        assertEquals(saved.id, vm.state.value.selectedPresetId)
        assertNull(vm.state.value.draft)
        assertTrue(scheduler.started.isEmpty())
    }

    @Test
    fun `a user's preset can be edited and deleted`() = runTest {
        val folder = "content://tree/camera"
        folders.add(folder)
        val id = presets.save(ScanPreset(0, "Camera roll", ScanScope.Folders(folders.folders.value), persistentSetOf(MediaKind.IMAGE)))
        val vm = viewModel()
        assertTrue(vm.state.value.presets.first().editable)

        vm.onIntent(ScanIntent.EditPresetClicked(id))
        assertEquals(PresetDraftUi(id, "Camera roll"), vm.state.value.draft)
        assertEquals(setOf(MediaKind.IMAGE), vm.state.value.kinds.filter { it.selected }.map { it.kind }.toSet())
        vm.onIntent(ScanIntent.NextClicked)
        assertEquals(ScopeKind.FOLDERS, vm.state.value.selectedScope)
        vm.onIntent(ScanIntent.DraftNameChanged("Camera"))
        vm.onIntent(ScanIntent.SavePresetClicked)
        assertEquals("Camera", presets.rows.value.single().name)

        vm.onIntent(ScanIntent.DeletePresetClicked(id))
        assertEquals(id, vm.state.value.confirmDelete?.id)
        vm.onIntent(ScanIntent.DeleteConfirmed)
        assertTrue(presets.rows.value.isEmpty())
    }

    @Test
    fun `a folder picked on the where step is added and selected`() {
        val vm = viewModel()
        vm.toWhere()

        vm.onIntent(ScanIntent.AddFolderClicked)
        vm.onIntent(ScanIntent.FolderPicked("content://tree/1"))

        assertEquals(ScopeKind.FOLDERS, vm.state.value.selectedScope)
        assertEquals(listOf(FolderOptionUi("content://tree/1", "Folder", selected = true)), vm.state.value.folders)
        vm.onIntent(ScanIntent.StartClicked)
        assertEquals(listOf("content://tree/1"), (scheduler.started.single() as ScanScope.Folders).folders.map { it.uri })
    }

    @Test
    fun `a started scan is followed through running to its result`() = runTest {
        runs.latest.value = completed(id = 4, duplicates = 0)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.progress.collect {} }
        vm.toWhere()
        vm.onIntent(ScanIntent.ScopeSelected(ScopeKind.MEDIA_ONLY))

        vm.onIntent(ScanIntent.StartClicked)
        assertEquals(listOf<ScanScope>(ScanScope.MediaOnly), scheduler.started)
        assertTrue(vm.state.value.starting)

        runs.latest.value = ScanRunInfo(5, ScopeKind.MEDIA_ONLY, ScanStatus.RUNNING, 0, null, null)
        scheduler.progress.value = ScanProgress(ScanStage.EXACT, 30, 120)
        assertEquals(ScanPhase.Running, vm.state.value.phase)
        assertEquals(UiText.res(R.string.scan_compared, "30", "120"), vm.progress.value!!.count)
        assertEquals(0.25f, vm.progress.value!!.fraction)

        scheduler.progress.value = null
        runs.latest.value = completed(id = 5, duplicates = 3)
        val done = vm.state.value.phase as ScanPhase.Done
        assertTrue(done.result.hasResults)
        assertFalse(vm.state.value.starting)
    }

    @Test
    fun `a scan too short to be seen running still shows its result`() {
        val vm = viewModel()
        vm.toWhere()
        vm.onIntent(ScanIntent.StartClicked)

        runs.latest.value = completed(id = 1, duplicates = 0)

        val done = vm.state.value.phase as ScanPhase.Done
        assertFalse(done.result.hasResults)
        assertEquals(UiText.res(R.string.scan_done_none), done.result.body)
    }

    @Test
    fun `an old finished run never shows as this screen's result`() {
        runs.latest.value = completed(id = 9, duplicates = 2)

        assertEquals(ScanPhase.Setup, viewModel().state.value.phase)
    }

    @Test
    fun `starting a scope that became unreadable explains why`() = runTest {
        val vm = viewModel()
        val effects = mutableListOf<ScanEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
        vm.toWhere()
        access.state = StorageAccessState(MediaAccess.NONE, allFiles = false)

        vm.onIntent(ScanIntent.StartClicked)

        assertTrue(scheduler.started.isEmpty())
        assertFalse(vm.state.value.starting)
        assertEquals(listOf<ScanEffect>(ScanEffect.ShowMessage(DomainError.PermissionDenied.toUiText())), effects)
    }

    @Test
    fun `cancel stops the worker and the run ends as cancelled`() {
        val vm = viewModel()
        runs.latest.value = ScanRunInfo(2, ScopeKind.WHOLE_DEVICE, ScanStatus.RUNNING, 0, null, null)
        scheduler.progress.value = ScanProgress(ScanStage.INDEXING, 100, null)

        vm.onIntent(ScanIntent.CancelClicked)
        assertTrue(scheduler.cancelled)
        assertTrue(vm.state.value.cancelling)

        scheduler.progress.value = null
        runs.latest.value = ScanRunInfo(2, ScopeKind.WHOLE_DEVICE, ScanStatus.CANCELLED, 0, 1, null)
        val done = vm.state.value.phase as ScanPhase.Done
        assertEquals(UiText.res(R.string.scan_cancelled_title), done.result.title)
        assertFalse(vm.state.value.cancelling)
    }

    @Test
    fun `skip AI is offered during the AI stage and asks the running scan to skip it`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.progress.collect {} }
        runs.latest.value = ScanRunInfo(3, ScopeKind.WHOLE_DEVICE, ScanStatus.RUNNING, 0, null, null)
        scheduler.progress.value = ScanProgress(ScanStage.NEAR, 1, 10)
        assertFalse(vm.progress.value!!.aiStage)

        scheduler.progress.value = ScanProgress(ScanStage.SEMANTIC, 1, 10)
        assertTrue(vm.progress.value!!.aiStage)
        vm.onIntent(ScanIntent.SkipAiClicked)

        assertTrue(skip.requested.value)
        assertTrue(vm.state.value.aiSkipped)
        assertFalse(scheduler.cancelled)
    }

    @Test
    fun `the similar files switch is saved`() {
        val vm = viewModel()

        vm.onIntent(ScanIntent.NearToggled(false))

        assertFalse(settings.settings.value.nearDuplicatesEnabled)
        assertFalse(vm.state.value.nearEnabled)
    }

    @Test
    fun `files per second wait for a full second`() {
        assertNull(filesPerSecond(50, 500_000_000))
        assertEquals(50, filesPerSecond(100, 2_000_000_000))
    }
}
