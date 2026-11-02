package com.vipuljha.dupedigger.presentation.compare

import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.usecase.ConfirmTrashUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.ObserveGroupUseCase
import com.vipuljha.dupedigger.domain.usecase.RestoreQuarantinedUseCase
import com.vipuljha.dupedigger.domain.usecase.UndoRemovalUseCase
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import com.vipuljha.dupedigger.presentation.FakeQuarantine
import com.vipuljha.dupedigger.presentation.FakeReclaimGroups
import com.vipuljha.dupedigger.presentation.FakeRemover
import com.vipuljha.dupedigger.presentation.common.FileRemoval
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class CompareViewModelTest {
    private val repo = FakeReclaimGroups()
    private val index = FakeFileIndex(setOf(1L, 2L))
    private val remover = FakeRemover()

    private fun member(id: Long, path: String) = GroupMember(id, "file:$path", path, size = 100, mtime = 1, name = path.substringAfterLast('/'))

    private val group = GroupWithMembers(
        7, "EXACT:100:a", MatchTier.EXACT, MediaKind.IMAGE,
        persistentListOf(member(1, "/storage/emulated/0/DCIM/a.jpg"), member(2, "/storage/emulated/0/Download/a.jpg")),
        keepChoice = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo.detail.value = group
        repo.groups += group
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `delete removes the compared copy, never the keeper, and offers Undo`() = runTest {
        val vm = CompareViewModel(
            group.fingerprint, leftId = 1, rightId = 2, ObserveGroupUseCase(repo),
            FileRemoval(DeleteDuplicatesUseCase(repo, index, remover), ConfirmTrashUseCase(index), UndoRemovalUseCase(remover, RestoreQuarantinedUseCase(FakeQuarantine()))),
        )
        val events = mutableListOf<FileRemoval.Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.collect { events += (it as CompareEffect.Removal).event } }

        vm.onIntent(CompareIntent.DeleteClicked)

        assertEquals(setOf(1L), index.ids)
        assertNotNull((events.single() as FileRemoval.Event.Message).undo)
    }
}
