package com.vipuljha.dupedigger.presentation.group

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaInfo
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ViewTarget
import com.vipuljha.dupedigger.domain.repository.FileInspector
import com.vipuljha.dupedigger.domain.usecase.ChooseKeeperUseCase
import com.vipuljha.dupedigger.domain.usecase.ConfirmTrashUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.GetViewTargetUseCase
import com.vipuljha.dupedigger.domain.usecase.MarkNotDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.ObserveGroupUseCase
import com.vipuljha.dupedigger.domain.usecase.ReadMediaInfoUseCase
import com.vipuljha.dupedigger.domain.usecase.RestoreQuarantinedUseCase
import com.vipuljha.dupedigger.domain.usecase.UndoRemovalUseCase
import com.vipuljha.dupedigger.presentation.FakeFileIndex
import com.vipuljha.dupedigger.presentation.FakeQuarantine
import com.vipuljha.dupedigger.presentation.FakeReclaimGroups
import com.vipuljha.dupedigger.presentation.FakeRemover
import com.vipuljha.dupedigger.presentation.common.FileRemoval
import kotlinx.collections.immutable.persistentListOf
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class GroupViewModelTest {
    private val repo = FakeReclaimGroups()
    private val saved = Locale.getDefault()
    private val inspector = object : FileInspector {
        override suspend fun mediaInfo(member: GroupMember, kind: MediaKind) = MediaInfo(4000, 3000)
        override suspend fun viewTarget(member: GroupMember) = ViewTarget("content://media/external/images/media/${member.fileId}", "image/jpeg")
    }
    private val mtime = LocalDate.of(2026, 9, 28).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun member(id: Long, path: String) = GroupMember(id, "file:$path", path, size = 2_000_000, mtime = mtime, name = path.substringAfterLast('/'))

    private val group = GroupWithMembers(
        7, "EXACT:2000000:abc", MatchTier.EXACT, MediaKind.IMAGE,
        persistentListOf(member(1, "/storage/emulated/0/Download/a.jpg"), member(2, "/storage/emulated/0/DCIM/Camera/a.jpg")),
        keepChoice = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Locale.setDefault(Locale.US)
        repo.detail.value = group
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Locale.setDefault(saved)
    }

    private fun viewModel() = GroupViewModel(
        group.fingerprint,
        ObserveGroupUseCase(repo),
        ChooseKeeperUseCase(repo),
        MarkNotDuplicatesUseCase(repo),
        ReadMediaInfoUseCase(inspector),
        GetViewTargetUseCase(inspector),
        removal(),
    )

    private val index = FakeFileIndex(setOf(1L, 2L))
    private val remover = FakeRemover()
    private val quarantine = FakeQuarantine()

    private fun removal() = FileRemoval(
        DeleteDuplicatesUseCase(repo, index, remover),
        ConfirmTrashUseCase(index),
        UndoRemovalUseCase(remover, RestoreQuarantinedUseCase(quarantine)),
    )

    private fun TestScope.removalEvents(vm: GroupViewModel) = mutableListOf<FileRemoval.Event>().also { events ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.effect.collect { if (it is GroupEffect.Removal) events += it.event }
        }
    }

    private fun GroupViewModel.detail() = (state.value.content as ContentState.Success).data!!

    @Test
    fun `the keeper comes first and each file shows where it is, its size and date`() {
        val detail = viewModel().detail()

        assertEquals(listOf(2L, 1L), detail.members.map { it.fileId })
        assertEquals(listOf(true, false), detail.members.map { it.isKeeper })
        assertEquals(UiText.Raw("/storage/emulated/0/DCIM/Camera"), detail.members.first().location)
        assertEquals(UiText.res(R.string.group_member_details, UiText.res(R.string.size_mb, "2.0"), UiText.Raw("Sep 28, 2026")), detail.members.first().details)
        assertEquals("file:/storage/emulated/0/DCIM/Camera/a.jpg", detail.members.first().thumbnail)
    }

    @Test
    fun `dimensions are read for each image`() {
        val vm = viewModel()

        assertEquals(UiText.res(R.string.group_resolution, 4000, 3000), vm.state.value.resolutions[1L])
        assertEquals(2, vm.state.value.resolutions.size)
    }

    @Test
    fun `keeping another file saves the choice by fingerprint and moves it first`() {
        val vm = viewModel()

        vm.onIntent(GroupIntent.KeepClicked(1))

        assertEquals("file:/storage/emulated/0/Download/a.jpg", repo.keepChoices[group.fingerprint])
        assertEquals(1L, vm.detail().members.first().fileId)
    }

    @Test
    fun `not duplicates is saved and closes the screen`() = runTest {
        val vm = viewModel()
        val effects = mutableListOf<GroupEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }

        vm.onIntent(GroupIntent.NotDuplicatesClicked)

        assertEquals(listOf(group.fingerprint), repo.notDuplicates)
        assertEquals(listOf<GroupEffect>(GroupEffect.Close), effects)
    }

    @Test
    fun `a group left with one file reads as gone`() {
        repo.detail.value = group.copy(members = persistentListOf(group.members.first()))

        assertNull((viewModel().state.value.content as ContentState.Success).data)
    }

    @Test
    fun `open hands over a shareable uri`() = runTest {
        val vm = viewModel()
        val effects = mutableListOf<GroupEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }

        vm.onIntent(GroupIntent.OpenClicked(2))

        assertTrue(effects.single() == GroupEffect.OpenFile("content://media/external/images/media/2", "image/jpeg"))
    }

    @Test
    fun `a copy is deleted in place with Undo, and the keeper can't be deleted here`() = runTest {
        repo.groups += group
        val vm = viewModel()
        val events = removalEvents(vm)

        vm.onIntent(GroupIntent.DeleteClicked(2))
        assertEquals(setOf(1L, 2L), index.ids)
        vm.onIntent(GroupIntent.DeleteClicked(1))

        assertEquals(setOf(2L), index.ids)
        val message = events.single() as FileRemoval.Event.Message
        assertEquals(listOf(101L), message.undo!!.quarantineIds)
        vm.onIntent(GroupIntent.UndoClicked(message.undo!!))
        assertEquals(listOf(101L), quarantine.restored)
    }

    @Test
    fun `media waiting for the trash prompt is finished even after the group dissolved`() = runTest {
        repo.groups += group
        remover.needConsent += 1L
        val vm = viewModel()
        val events = removalEvents(vm)

        vm.onIntent(GroupIntent.DeleteClicked(1))
        assertEquals(FileRemoval.Event.LaunchTrash("prompt"), events.single())
        repo.detail.value = null
        vm.onIntent(GroupIntent.TrashAnswered(accepted = true))

        assertEquals(setOf(2L), index.ids)
        assertTrue(events.last() is FileRemoval.Event.Message)
    }
}
