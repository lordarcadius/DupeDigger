package com.vipuljha.dupedigger.presentation.quarantine

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.toUiText
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

    private fun viewModel() = QuarantineViewModel(quarantine, RestoreQuarantinedUseCase(quarantine), DeleteQuarantinedUseCase(quarantine))

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
    fun `restore puts the file back and says so`() = runTest {
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.RestoreClicked(1))

        assertEquals(listOf(1L), quarantine.restored)
        assertEquals(listOf(2L), vm.items().map { it.id })
        assertEquals(listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(UiText.plural(R.plurals.restored, 1, "1"))), effects)
        assertTrue(vm.state.value.busy.isEmpty())
    }

    @Test
    fun `a failed restore keeps the file and explains`() = runTest {
        quarantine.failing += 1L
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.RestoreClicked(1))

        assertEquals(listOf(1L, 2L), vm.items().map { it.id })
        assertEquals(listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(UiText.plural(R.plurals.restore_failed, 1, "1"))), effects)
    }

    @Test
    fun `delete permanently happens only after the confirmation`() = runTest {
        val vm = viewModel()
        val effects = effectsOf(vm)
        val item = vm.items().last()

        vm.onIntent(QuarantineIntent.DeleteClicked(item))
        assertEquals(item, vm.state.value.confirmDelete)
        vm.onIntent(QuarantineIntent.DeleteDismissed)
        assertEquals(2, vm.items().size)

        vm.onIntent(QuarantineIntent.DeleteClicked(item))
        vm.onIntent(QuarantineIntent.DeleteConfirmed)

        assertNull(vm.state.value.confirmDelete)
        assertEquals(listOf(1L), vm.items().map { it.id })
        assertEquals(listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(UiText.res(R.string.quarantine_deleted, "report.pdf"))), effects)
    }

    @Test
    fun `a failed permanent delete reports the error`() = runTest {
        quarantine.failing += 2L
        val vm = viewModel()
        val effects = effectsOf(vm)

        vm.onIntent(QuarantineIntent.DeleteClicked(vm.items().last()))
        vm.onIntent(QuarantineIntent.DeleteConfirmed)

        assertEquals(listOf<QuarantineEffect>(QuarantineEffect.ShowMessage(DomainError.Io.toUiText())), effects)
    }
}
