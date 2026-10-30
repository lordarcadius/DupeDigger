package com.vipuljha.dupedigger.presentation.scan

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.model.ScopeKind
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.usecase.CancelScanUseCase
import com.vipuljha.dupedigger.domain.usecase.GetAvailableScopesUseCase
import com.vipuljha.dupedigger.domain.usecase.SetNearDuplicatesEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.StartScanUseCase
import com.vipuljha.dupedigger.presentation.FakeFolders
import com.vipuljha.dupedigger.presentation.FakeScanRuns
import com.vipuljha.dupedigger.presentation.FakeScheduler
import com.vipuljha.dupedigger.presentation.FakeSettings
import com.vipuljha.dupedigger.presentation.FakeStorageAccess
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

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ScanViewModel(
        settings, runs, scheduler,
        GetAvailableScopesUseCase(access, folders),
        StartScanUseCase(access, folders, scheduler),
        CancelScanUseCase(scheduler),
        SetNearDuplicatesEnabledUseCase(settings),
        SetSemanticEnabledUseCase(settings),
    )

    private fun completed(id: Long, duplicates: Int) = ScanRunInfo(
        id, ScopeKind.WHOLE_DEVICE, ScanStatus.COMPLETED, 0, 1,
        ScanSummary(IndexStats(), exactGroups = 1, duplicateFiles = duplicates, reclaimableBytes = 5_000, hashesComputed = 0, unreadable = 0),
    )

    @Test
    fun `setup offers the readable scopes and picks the widest`() {
        val state = viewModel().state.value

        assertEquals(ScanPhase.Setup, state.phase)
        assertEquals(listOf(ScopeKind.WHOLE_DEVICE, ScopeKind.MEDIA_ONLY), state.scopes.map { it.kind })
        assertEquals(ScopeKind.WHOLE_DEVICE, state.selectedScope)
    }

    @Test
    fun `a started scan is followed through running to its result`() = runTest {
        runs.latest.value = completed(id = 4, duplicates = 0)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.progress.collect {} }
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
