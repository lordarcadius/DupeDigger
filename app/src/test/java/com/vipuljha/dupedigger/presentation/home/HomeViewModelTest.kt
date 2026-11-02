package com.vipuljha.dupedigger.presentation.home

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
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
import org.junit.Assert.assertNull
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
    fun `the last scan shows how long it took and how many files it scanned`() {
        val summary = ScanSummary(IndexStats(added = 1_000, changed = 200, unchanged = 34, removed = 5), 3, 4, 5_000, 0, 0)
        runs.latest.value = ScanRunInfo(1, ScopeKind.WHOLE_DEVICE, ScanStatus.COMPLETED, startedAt = 10_000, finishedAt = 135_000, summary = summary)

        val stats = viewModel().model().lastScan!!.stats

        assertEquals(
            UiText.res(
                R.string.home_last_scan_stats,
                UiText.res(R.string.home_last_scan_took, UiText.res(R.string.duration_minutes, "2", "5")),
                UiText.plural(R.plurals.home_files_scanned, 1_234, "1,234"),
            ),
            stats,
        )
    }

    @Test
    fun `a cancelled scan shows only how long it ran`() {
        runs.latest.value = ScanRunInfo(1, ScopeKind.MEDIA_ONLY, ScanStatus.CANCELLED, startedAt = 0, finishedAt = 42_000, summary = null)

        assertEquals(UiText.res(R.string.home_last_scan_took, UiText.res(R.string.duration_seconds, "42")), viewModel().model().lastScan!!.stats)
    }

    @Test
    fun `a running scan shows on home even before it finishes once`() {
        val vm = viewModel()
        scheduler.progress.value = ScanProgress(ScanStage.INDEXING, 10, null)

        assertTrue(vm.model().scanning)
        assertFalse(vm.model().isEmpty)
    }

    @Test
    fun `a running scan's live progress shows on home`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.progress.collect {} }
        assertNull(vm.progress.value)

        scheduler.progress.value = ScanProgress(ScanStage.EXACT, 30, 120)

        assertEquals(UiText.res(R.string.scan_stage_exact), vm.progress.value!!.stage)
        assertEquals(UiText.res(R.string.scan_compared, "30", "120"), vm.progress.value!!.count)
        assertEquals(0.25f, vm.progress.value!!.fraction)
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
