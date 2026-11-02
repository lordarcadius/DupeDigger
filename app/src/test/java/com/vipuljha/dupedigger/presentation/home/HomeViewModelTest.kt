package com.vipuljha.dupedigger.presentation.home

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScopeKind
import com.vipuljha.dupedigger.presentation.FakeReclaimGroups
import com.vipuljha.dupedigger.presentation.FakeScanRuns
import com.vipuljha.dupedigger.presentation.FakeScheduler
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {
    private val groups = FakeReclaimGroups()
    private val runs = FakeScanRuns()
    private val scheduler = FakeScheduler()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(groups, runs, scheduler)

    private fun HomeViewModel.model() = (state.value.content as ContentState.Success).data

    @Test
    fun `before any scan home is empty`() {
        assertTrue(viewModel().model().isEmpty)
    }

    @Test
    fun `the summary lists what each kind can free, largest first, and the last scan`() {
        groups.reclaimable.value = listOf(
            KindReclaim(MediaKind.DOCUMENT, groups = 1, files = 1, bytes = 2_000),
            KindReclaim(MediaKind.IMAGE, groups = 3, files = 5, bytes = 9_000_000),
        )
        runs.latest.value = ScanRunInfo(1, ScopeKind.MEDIA_ONLY, ScanStatus.COMPLETED, 0, System.currentTimeMillis(), null)

        val model = viewModel().model()

        assertFalse(model.isEmpty)
        assertEquals(formatBytes(9_002_000), model.reclaimTotal)
        assertEquals(listOf(MediaKind.IMAGE, MediaKind.DOCUMENT), model.kinds.map { it.kind })
        assertEquals(UiText.res(R.string.home_scan_status_completed), model.lastScan!!.status)
        assertEquals(UiText.res(R.string.scope_media), model.lastScan!!.scope)
    }

    @Test
    fun `a running scan shows on home even before it finishes once`() {
        val vm = viewModel()
        scheduler.progress.value = ScanProgress(ScanStage.INDEXING, 10, null)

        assertTrue(vm.model().scanning)
        assertFalse(vm.model().isEmpty)
    }

    @Test
    fun `start scan opens the scan screen`() = runTest {
        val vm = viewModel()
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }

        vm.onIntent(HomeIntent.OpenScanClicked)

        assertEquals(listOf<HomeEffect>(HomeEffect.OpenScan), effects)
        assertTrue(scheduler.started.isEmpty())
    }
}
