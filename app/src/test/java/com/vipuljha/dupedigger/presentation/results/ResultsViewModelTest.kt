package com.vipuljha.dupedigger.presentation.results

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.domain.detector.toItem
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateGroupsUseCase
import com.vipuljha.dupedigger.domain.usecase.SelectAllButKeepersUseCase
import com.vipuljha.dupedigger.presentation.FakeReclaimGroups
import com.vipuljha.dupedigger.presentation.FakeScanRuns
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ResultsViewModelTest {
    private val repo = FakeReclaimGroups()
    private val runs = FakeScanRuns()
    private val saved = Locale.getDefault()

    private fun member(id: Long, path: String) = GroupMember(id, "file:$path", path, size = 100, mtime = 1, name = path.substringAfterLast('/'))

    /** Images: the camera copy is kept. Documents: the user chose to keep the second file. */
    private val images = GroupWithMembers(
        1, "EXACT:100:a", MatchTier.EXACT, MediaKind.IMAGE,
        persistentListOf(member(1, "/storage/emulated/0/DCIM/a.jpg"), member(2, "/storage/emulated/0/Download/a.jpg")),
        keepChoice = null,
    )
    private val documents = GroupWithMembers(
        2, "EXACT:100:b", MatchTier.EXACT, MediaKind.DOCUMENT,
        persistentListOf(member(3, "/storage/emulated/0/Documents/b.pdf"), member(4, "/storage/emulated/0/Download/b.pdf")),
        keepChoice = "file:/storage/emulated/0/Download/b.pdf",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.US)
        repo.groups += listOf(images, documents)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(saved)
    }

    private fun viewModel() = ResultsViewModel(repo, runs, GetDuplicateGroupsUseCase(repo), SelectAllButKeepersUseCase(repo))

    @Test
    fun `select duplicates takes every file but each group's keeper`() {
        val vm = viewModel()

        vm.onIntent(ResultsIntent.SelectAllButKeepers)

        assertEquals(setOf(2L, 3L), vm.state.value.selection.keys)
        assertEquals(UiText.plural(R.plurals.results_selected, 2, "2", formatBytes(200)), vm.state.value.selectionSummary)
        assertFalse(vm.state.value.selecting)
    }

    @Test
    fun `a category limits what is selected and changing it clears the selection`() {
        val vm = viewModel()
        vm.onIntent(ResultsIntent.KindSelected(MediaKind.IMAGE))
        vm.onIntent(ResultsIntent.SelectAllButKeepers)

        assertEquals(GroupFilter(kind = MediaKind.IMAGE), repo.filters.last())
        assertEquals(setOf(2L), vm.state.value.selection.keys)

        vm.onIntent(ResultsIntent.KindSelected(null))
        assertTrue(vm.state.value.selection.isEmpty())
        assertNull(vm.state.value.selectionSummary)
    }

    @Test
    fun `toggling a group selects and unselects its removable files`() {
        val vm = viewModel()
        val row = documents.toItem().toRow()

        vm.onIntent(ResultsIntent.GroupToggled(row))
        assertEquals(setOf(3L), vm.state.value.selection.keys)

        vm.onIntent(ResultsIntent.GroupToggled(row.copy(selected = true)))
        assertTrue(vm.state.value.selection.isEmpty())
    }

    @Test
    fun `a new scan clears the selection`() {
        runs.latest.value = ScanRunInfo(1, null, ScanStatus.COMPLETED, 0, 1, null)
        val vm = viewModel()
        vm.onIntent(ResultsIntent.SelectAllButKeepers)

        runs.latest.value = ScanRunInfo(2, null, ScanStatus.RUNNING, 2, null, null)

        assertTrue(vm.state.value.selection.isEmpty())
    }

    @Test
    fun `chips list all, then each kind with groups, with counts`() {
        repo.reclaimable.value = listOf(
            KindReclaim(MediaKind.DOCUMENT, groups = 2, files = 2, bytes = 10),
            KindReclaim(MediaKind.IMAGE, groups = 3, files = 4, bytes = 20),
        )

        val chips = viewModel().state.value.kindChips

        assertEquals(listOf(null, MediaKind.IMAGE, MediaKind.DOCUMENT), chips.map { it.kind })
        assertEquals(UiText.res(R.string.results_chip_count, UiText.res(R.string.results_chip_all), "5"), chips.first().label)
    }

    @Test
    fun `a row shows the keeper's name and what removing the rest frees`() {
        val row = images.toItem().toRow()

        assertEquals("a.jpg", row.title)
        assertEquals(mapOf(2L to 100L), row.removable)
        assertEquals(UiText.res(R.string.results_frees, formatBytes(100)), row.reclaim)
    }
}
