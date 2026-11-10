package com.vipuljha.dupedigger.presentation.results

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.domain.detector.toItem
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.KeepRule
import com.vipuljha.dupedigger.domain.model.GroupFacet
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.usecase.ConfirmTrashUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateFoldersUseCase
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateGroupsUseCase
import com.vipuljha.dupedigger.domain.usecase.RestoreQuarantinedUseCase
import com.vipuljha.dupedigger.domain.usecase.UndoRemovalUseCase
import com.vipuljha.dupedigger.domain.usecase.SelectAllButKeepersUseCase
import com.vipuljha.dupedigger.presentation.FakeDeviceStorage
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import com.vipuljha.dupedigger.presentation.FakeQuarantine
import com.vipuljha.dupedigger.presentation.FakeReclaimGroups
import com.vipuljha.dupedigger.presentation.FakeRemover
import com.vipuljha.dupedigger.presentation.FakeScanRuns
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

    private val index = FakeFileIndex((1L..4L).toSet())
    private val remover = FakeRemover()
    private val quarantine = FakeQuarantine()

    private fun viewModel() = ResultsViewModel(
        repo, runs, GetDuplicateGroupsUseCase(repo), SelectAllButKeepersUseCase(repo), GetDuplicateFoldersUseCase(repo),
        DeleteDuplicatesUseCase(repo, index, remover), ConfirmTrashUseCase(index), UndoRemovalUseCase(remover, RestoreQuarantinedUseCase(quarantine)),
        FakeDeviceStorage(),
    )

    private fun TestScope.effectsOf(vm: ResultsViewModel) = mutableListOf<ResultsEffect>().also { effects ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
    }

    @Test
    fun `delete asks first, naming the count and the space`() {
        val vm = viewModel()
        vm.onIntent(ResultsIntent.SelectAllToggled)

        vm.onIntent(ResultsIntent.DeleteClicked)
        assertEquals(
            DeleteConfirmUi(2, UiText.plural(R.plurals.results_delete_title, 2, "2"), UiText.res(R.string.results_delete_body, formatBytes(200))),
            vm.state.value.confirmDelete,
        )

        vm.onIntent(ResultsIntent.DeleteDismissed)
        assertNull(vm.state.value.confirmDelete)
        assertEquals((1L..4L).toSet(), index.ids)
    }

    @Test
    fun `a confirmed delete removes the files, clears the selection and offers undo`() = runTest {
        remover.trashable += 2L
        val vm = viewModel()
        val effects = effectsOf(vm)
        vm.onIntent(ResultsIntent.SelectAllToggled)
        vm.onIntent(ResultsIntent.DeleteClicked)

        vm.onIntent(ResultsIntent.DeleteConfirmed)

        assertEquals(setOf(1L, 4L), index.ids)
        assertTrue(vm.state.value.selection.isEmpty())
        assertFalse(vm.state.value.deleting)
        val removed = UiText.plural(R.plurals.results_removed, 2, "2", formatBytes(200))
        val undo = UndoUi(persistentListOf("media:2"), persistentListOf(103L))
        assertEquals(listOf<ResultsEffect>(ResultsEffect.ShowMessage(removed, undo)), effects)

        vm.onIntent(ResultsIntent.UndoClicked(undo))
        assertEquals(listOf("media:2"), remover.untrashed)
        assertEquals(listOf(103L), quarantine.restored)
        assertEquals(ResultsEffect.ShowMessage(UiText.plural(R.plurals.restored, 2, "2")), effects.last())
    }

    @Test
    fun `media waiting for consent leaves the index only once the user agrees`() = runTest {
        remover.needConsent += 2L
        val vm = viewModel()
        val effects = effectsOf(vm)
        vm.onIntent(ResultsIntent.SelectAllToggled)

        vm.onIntent(ResultsIntent.DeleteConfirmed)

        assertEquals(ResultsEffect.LaunchTrash("prompt"), effects.last())
        assertTrue(2L in index.ids)

        vm.onIntent(ResultsIntent.TrashAnswered(accepted = true))
        assertTrue(2L !in index.ids)
        assertEquals(ResultsEffect.ShowMessage(UiText.plural(R.plurals.results_removed, 1, "1", formatBytes(100))), effects.last())
    }

    @Test
    fun `declining the trash prompt keeps the media`() = runTest {
        remover.needConsent += 2L
        val vm = viewModel()
        vm.onIntent(ResultsIntent.SelectAllToggled)
        vm.onIntent(ResultsIntent.DeleteConfirmed)

        vm.onIntent(ResultsIntent.TrashAnswered(accepted = false))

        assertTrue(2L in index.ids)
    }

    @Test
    fun `a delete that would empty a group is refused with a reason`() = runTest {
        val vm = viewModel()
        val effects = effectsOf(vm)
        vm.onIntent(ResultsIntent.GroupToggled(images.toItem().toRow().copy(removable = persistentMapOf(1L to 100L, 2L to 100L))))

        vm.onIntent(ResultsIntent.DeleteConfirmed)

        assertEquals(listOf<ResultsEffect>(ResultsEffect.ShowMessage(DomainError.WouldRemoveAllCopies.toUiText())), effects)
        assertEquals((1L..4L).toSet(), index.ids)
    }

    @Test
    fun `select duplicates takes every file but each group's keeper`() {
        val vm = viewModel()

        vm.onIntent(ResultsIntent.SelectAllToggled)

        assertEquals(setOf(2L, 3L), vm.state.value.selection.keys)
        assertEquals(UiText.plural(R.plurals.results_selected, 2, "2", formatBytes(200)), vm.state.value.selectionSummary)
        assertFalse(vm.state.value.selecting)
    }

    @Test
    fun `a category limits what is selected and changing it clears the selection`() {
        val vm = viewModel()
        vm.onIntent(ResultsIntent.KindSelected(MediaKind.IMAGE))
        vm.onIntent(ResultsIntent.SelectAllToggled)

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
        vm.onIntent(ResultsIntent.SelectAllToggled)

        runs.latest.value = ScanRunInfo(2, null, ScanStatus.RUNNING, 2, null, null)

        assertTrue(vm.state.value.selection.isEmpty())
    }

    @Test
    fun `a new keeper drops only that file from the selection, leaving the rest`() {
        val vm = viewModel()
        vm.onIntent(ResultsIntent.SelectAllToggled)
        assertEquals(setOf(2L, 3L), vm.state.value.selection.keys)

        repo.groups[0] = images.copy(keepChoice = "file:/storage/emulated/0/Download/a.jpg")
        repo.decisions.tryEmit(Unit)

        assertEquals(setOf(3L), vm.state.value.selection.keys)
    }

    @Test
    fun `a file that left its group drops out of the selection`() {
        val vm = viewModel()
        vm.onIntent(ResultsIntent.SelectAllToggled)

        repo.groups.remove(documents)
        repo.decisions.tryEmit(Unit)

        assertEquals(setOf(2L), vm.state.value.selection.keys)
    }

    @Test
    fun `tier chips appear per tier and choosing one filters the list and clears the selection`() {
        repo.facets.value = listOf(
            GroupFacet(MediaKind.IMAGE, MatchTier.EXACT, groups = 3, files = 3, bytes = 300),
            GroupFacet(MediaKind.IMAGE, MatchTier.NEAR, groups = 2, files = 2, bytes = 200),
        )
        val vm = viewModel()
        assertEquals(listOf(null, MatchTier.EXACT, MatchTier.NEAR), vm.state.value.tierChips.map { it.tier })
        vm.onIntent(ResultsIntent.SelectAllToggled)

        vm.onIntent(ResultsIntent.TierSelected(MatchTier.NEAR))

        assertTrue(vm.state.value.selection.isEmpty())
        vm.onIntent(ResultsIntent.SelectAllToggled)
        assertEquals(GroupFilter(tier = MatchTier.NEAR), repo.filters.last())
    }

    @Test
    fun `select all covers every tier the filter shows, and says how many are only similar`() {
        repo.groups[1] = documents.copy(tier = MatchTier.NEAR, keepChoice = null)
        val vm = viewModel()

        vm.onIntent(ResultsIntent.SelectAllToggled)

        assertEquals(GroupFilter(), repo.filters.last())
        assertEquals(setOf(2L, 4L), vm.state.value.selection.keys)

        vm.onIntent(ResultsIntent.DeleteClicked)
        assertEquals(
            UiText.plural(R.plurals.results_delete_similar, 1, "1"),
            vm.state.value.confirmDelete?.similarWarning,
        )
    }

    @Test
    fun `select all clears the selection once everything the filter offers is picked`() {
        repo.facets.value = listOf(GroupFacet(MediaKind.IMAGE, MatchTier.EXACT, groups = 2, files = 2, bytes = 200))
        val vm = viewModel()
        vm.onIntent(ResultsIntent.SelectAllToggled)
        assertTrue(vm.state.value.allSelected)

        vm.onIntent(ResultsIntent.SelectAllToggled)

        assertTrue(vm.state.value.selection.isEmpty())
    }

    @Test
    fun `chip counts follow the other filter, and the chosen chip stays even at zero`() {
        repo.facets.value = listOf(
            GroupFacet(MediaKind.IMAGE, MatchTier.EXACT, groups = 3, files = 3, bytes = 300),
            GroupFacet(MediaKind.VIDEO, MatchTier.NEAR, groups = 2, files = 2, bytes = 200),
        )
        val vm = viewModel()

        vm.onIntent(ResultsIntent.TierSelected(MatchTier.NEAR))

        assertEquals(listOf(null, MediaKind.VIDEO), vm.state.value.kindChips.map { it.kind })

        // Videos have no exact groups, but the chip the user is standing on stays, at zero, so they can leave it.
        vm.onIntent(ResultsIntent.KindSelected(MediaKind.VIDEO))
        vm.onIntent(ResultsIntent.TierSelected(MatchTier.EXACT))
        assertEquals(listOf(null, MediaKind.IMAGE, MediaKind.VIDEO), vm.state.value.kindChips.map { it.kind })
        assertEquals("0", (vm.state.value.kindChips.last().label as UiText.Resource).args[1])
        assertEquals(0, vm.state.value.selectable.files)
        assertEquals(UiText.plural(R.plurals.results_select_all_detail, 0, "0", formatBytes(0)), vm.state.value.selectable.detail)
    }

    @Test
    fun `a near row is tagged with its similarity, capped below 100 percent, and exact rows are not`() {
        val near = images.copy(tier = MatchTier.NEAR, score = 1f).toItem().toRow()

        assertEquals(UiText.res(R.string.results_tier_tag, UiText.res(R.string.results_tier_near), UiText.Raw("99%")), near.tierTag)
        assertEquals(UiText.plural(R.plurals.results_similar_files, 2, "2"), near.detail)
        assertNull(images.toItem().toRow().tierTag)
    }

    @Test
    fun `chips list all, then each kind with groups, with counts`() {
        repo.facets.value = listOf(
            GroupFacet(MediaKind.DOCUMENT, MatchTier.EXACT, groups = 2, files = 2, bytes = 10),
            GroupFacet(MediaKind.IMAGE, MatchTier.EXACT, groups = 3, files = 4, bytes = 20),
        )

        val state = viewModel().state.value

        assertEquals(listOf(null, MediaKind.IMAGE, MediaKind.DOCUMENT), state.kindChips.map { it.kind })
        assertEquals(UiText.res(R.string.results_chip_count, UiText.res(R.string.results_chip_all), "5"), state.kindChips.first().label)
        assertEquals(6, state.selectable.files)
        assertEquals(30L, state.selectable.bytes)
        assertEquals(UiText.plural(R.plurals.results_select_all_detail, 6, "6", formatBytes(30)), state.selectable.detail)
    }

    @Test
    fun `a row shows the keeper's name and what removing the rest frees`() {
        val row = images.toItem().toRow()

        assertEquals("a.jpg", row.title)
        assertEquals(mapOf(2L to 100L), row.removable)
        assertEquals(UiText.res(R.string.results_frees, formatBytes(100)), row.reclaim)
    }

    @Test
    fun `a keep rule selects every other copy in one tap and stays on until the selection is changed by hand`() {
        val vm = viewModel()

        vm.onIntent(ResultsIntent.KeepRuleSelected(KeepRule.Largest))

        assertEquals(setOf(2L, 4L), vm.state.value.selection.keys)
        assertEquals(KeepRule.Largest, vm.state.value.keepRule)
        vm.onIntent(ResultsIntent.ClearSelection)
        assertNull(vm.state.value.keepRule)
    }

    @Test
    fun `keeping one folder's copies is picked from the folders the duplicates are in`() {
        val vm = viewModel()

        vm.onIntent(ResultsIntent.KeepFromFolderClicked)
        val choices = vm.state.value.folderChoices!!
        assertEquals("Download", choices.first().name)
        vm.onIntent(ResultsIntent.KeepRuleSelected(KeepRule.InFolder(choices.first().path)))

        assertNull(vm.state.value.folderChoices)
        assertEquals(setOf(1L, 3L), vm.state.value.selection.keys)
        assertEquals(KeepRule.InFolder("/storage/emulated/0/Download"), vm.state.value.keepRule)
    }

    @Test
    fun `a delete made from Group or Compare drops only the gone file, so the next delete can't trip on it`() {
        val vm = viewModel()
        vm.onIntent(ResultsIntent.SelectAllToggled)
        assertEquals(2, vm.state.value.selection.size)

        // The group shrank below two members, so neither of its files can go any more.
        repo.groups[1] = documents.copy(members = persistentListOf(member(4, "/storage/emulated/0/Download/b.pdf")))
        repo.decisions.tryEmit(Unit)

        assertEquals(setOf(2L), vm.state.value.selection.keys)
        assertEquals(UiText.plural(R.plurals.results_selected, 1, "1", formatBytes(100)), vm.state.value.selectionSummary)
    }
}
