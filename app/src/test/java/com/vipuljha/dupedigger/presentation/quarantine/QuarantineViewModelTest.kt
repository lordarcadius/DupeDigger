package com.vipuljha.dupedigger.presentation.quarantine

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.QuarantinedFile
import com.vipuljha.dupedigger.domain.usecase.DeleteQuarantinedUseCase
import com.vipuljha.dupedigger.domain.usecase.RestoreQuarantinedUseCase
import com.vipuljha.dupedigger.presentation.FakeQuarantine
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

class QuarantineViewModelTest {
    private val quarantine = FakeQuarantine()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        quarantine.files.value = listOf(
            QuarantinedFile(1, "notes.txt", "/storage/emulated/0/Documents/notes.txt", 10, movedAt = 0),
            QuarantinedFile(2, "report.pdf", "/storage/emulated/0/Download/report.pdf", 20, movedAt = 0),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        QuarantineViewModel(quarantine, RestoreQuarantinedUseCase(quarantine), DeleteQuarantinedUseCase(quarantine))

    private fun TestScope.effectsOf(vm: QuarantineViewModel) = mutableListOf<QuarantineEffect>().also { effects ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }
    }

    private fun QuarantineViewModel.items() = (state.value.content as ContentState.Success).data

    @Test
    fun `lists each file with the folder it came from`() {
        val item = viewModel().items().first()

        assertEquals("notes.txt", item.name)
        assertEquals(UiText.Raw("/storage/emulated/0/Documents"), item.folder)
    }

    @Test
    fun `nothing happens until a file is picked`() = runTest {
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.RestoreClicked)
        vm.onIntent(QuarantineIntent.DeleteClicked)

        assertTrue(quarantine.restored.isEmpty())
        assertNull(vm.state.value.confirmDelete)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `ticking a file marks it, and restore puts back only what was picked`() = runTest {
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.ItemToggled(1))
        assertTrue(vm.items().single { it.id == 1L }.selected)
        assertFalse(vm.items().single { it.id == 2L }.selected)

        vm.onIntent(QuarantineIntent.RestoreClicked)

        assertEquals(listOf(1L), quarantine.restored)
        assertEquals(listOf(2L), vm.items().map { it.id })
        assertEquals(listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(UiText.plural(R.plurals.restored, 1, "1"))), effects)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun `select all picks every file and a second tap clears them`() {
        val vm = viewModel()

        vm.onIntent(QuarantineIntent.SelectAllToggled)
        assertEquals(setOf(1L, 2L), vm.state.value.selection)
        assertTrue(vm.items().all { it.selected })

        vm.onIntent(QuarantineIntent.SelectAllToggled)
        assertTrue(vm.state.value.selection.isEmpty())
    }

    @Test
    fun `a failed restore keeps the file and explains`() = runTest {
        quarantine.failing += 1L
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.ItemToggled(1))
        vm.onIntent(QuarantineIntent.RestoreClicked)

        assertEquals(listOf(1L, 2L), vm.items().map { it.id })
        assertEquals(listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(UiText.plural(R.plurals.restore_failed, 1, "1"))), effects)
    }

    @Test
    fun `delete erases the picked files, but only after the confirmation`() = runTest {
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.SelectAllToggled)
        vm.onIntent(QuarantineIntent.DeleteClicked)
        assertEquals(2, vm.state.value.confirmDelete)
        vm.onIntent(QuarantineIntent.DeleteDismissed)
        assertNull(vm.state.value.confirmDelete)
        assertEquals(2, vm.items().size)

        vm.onIntent(QuarantineIntent.DeleteClicked)
        vm.onIntent(QuarantineIntent.DeleteConfirmed)

        assertNull(vm.state.value.confirmDelete)
        assertTrue(vm.items().isEmpty())
        assertEquals(
            listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(UiText.plural(R.plurals.quarantine_deleted_count, 2, "2"))),
            effects,
        )
    }

    @Test
    fun `a failed delete names how many could not go`() = runTest {
        quarantine.failing += 2L
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.SelectAllToggled)
        vm.onIntent(QuarantineIntent.DeleteClicked)
        vm.onIntent(QuarantineIntent.DeleteConfirmed)

        assertEquals(listOf(2L), vm.items().map { it.id })
        assertEquals(
            listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(UiText.plural(R.plurals.quarantine_delete_failed, 1, "1"))),
            effects,
        )
    }

    @Test
    fun `a file that leaves drops out of the selection`() {
        val vm = viewModel()
        vm.onIntent(QuarantineIntent.SelectAllToggled)

        quarantine.files.value = quarantine.files.value.filterNot { it.id == 1L }

        assertEquals(setOf(2L), vm.state.value.selection)
    }
}
