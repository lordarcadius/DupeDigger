package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.detector.keeperFor
import com.vipuljha.dupedigger.domain.model.DuplicateFolder
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.KeepRule
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.presentation.FakeReclaimGroups
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SelectAllButKeepersUseCaseTest {
    private val repo = FakeReclaimGroups()

    private fun member(id: Long, path: String) = GroupMember(id, "file:$path", path, size = 100, mtime = 1)

    @Test
    fun `a file that keeps one group is never selected from another, so deleting the selection empties nothing`() = runTest {
        val a1 = member(1, "/storage/emulated/0/Download/a.jpg")
        val a2 = member(2, "/storage/emulated/0/WhatsApp/Media/a.jpg")
        val c = member(3, "/storage/emulated/0/DCIM/c.jpg")
        // Exact {A1, A2} keeps A1; the near group {A1, C} keeps C, the camera photo.
        repo.groups += GroupWithMembers(1, "EXACT:100:a", MatchTier.EXACT, MediaKind.IMAGE, persistentListOf(a1, a2), keepChoice = null)
        repo.groups += GroupWithMembers(2, "NEAR:x", MatchTier.NEAR, MediaKind.IMAGE, persistentListOf(a1, c), keepChoice = null)

        val selected = SelectAllButKeepersUseCase(repo)(GroupFilter())

        assertEquals(listOf(2L), selected.map { it.fileId })
    }

    private fun file(id: Long, path: String, size: Long = 100, mtime: Long = 1) = GroupMember(id, "file:$path", path, size, mtime)

    private fun exact(id: Long, vararg members: GroupMember, keepChoice: String? = null) =
        GroupWithMembers(id, "EXACT:$id", MatchTier.EXACT, MediaKind.IMAGE, persistentListOf(*members), keepChoice)

    @Test
    fun `keep newest, oldest and largest each select every other copy`() = runTest {
        val old = file(1, "/storage/emulated/0/Pictures/a.jpg", size = 300, mtime = 10)
        val mid = file(2, "/storage/emulated/0/Download/a.jpg", size = 100, mtime = 20)
        val new = file(3, "/storage/emulated/0/WhatsApp/a.jpg", size = 200, mtime = 30)
        repo.groups += exact(1, old, mid, new)
        val select = SelectAllButKeepersUseCase(repo)

        assertEquals(listOf(1L, 2L), select(GroupFilter(), KeepRule.Newest).map { it.fileId }.sorted())
        assertEquals(listOf(2L, 3L), select(GroupFilter(), KeepRule.Oldest).map { it.fileId }.sorted())
        assertEquals(listOf(2L, 3L), select(GroupFilter(), KeepRule.Largest).map { it.fileId }.sorted())
    }

    @Test
    fun `an unknown time is never newest or oldest, and ties fall back to the default ranking`() = runTest {
        val unknown = file(1, "/storage/emulated/0/Download/a.jpg", mtime = 0)
        val camera = file(2, "/storage/emulated/0/DCIM/a.jpg", mtime = 5)
        val copy = file(3, "/storage/emulated/0/Download/b.jpg", mtime = 5)
        repo.groups += exact(1, unknown, camera, copy)

        assertEquals(listOf(1L, 3L), SelectAllButKeepersUseCase(repo)(GroupFilter(), KeepRule.Oldest).map { it.fileId }.sorted())
    }

    @Test
    fun `keeping one folder keeps its copy, and a group with none there keeps its best`() = runTest {
        repo.groups += exact(1, file(1, "/storage/emulated/0/DCIM/a.jpg"), file(2, "/storage/emulated/0/Backup/x/a.jpg"))
        repo.groups += exact(2, file(3, "/storage/emulated/0/DCIM/b.jpg"), file(4, "/storage/emulated/0/Download/b.jpg"))
        repo.groups += exact(3, file(5, "/storage/emulated/0/DCIM/c.jpg"), file(6, "/storage/emulated/0/Backup2/c.jpg"))

        val selected = SelectAllButKeepersUseCase(repo)(GroupFilter(), KeepRule.InFolder("/storage/emulated/0/Backup"))

        assertEquals(listOf(1L, 4L, 6L), selected.map { it.fileId }.sorted())
    }

    @Test
    fun `with any rule a file kept by one group is never selected from another`() = runTest {
        val a1 = file(1, "/storage/emulated/0/Download/a.jpg", mtime = 30)
        val a2 = file(2, "/storage/emulated/0/WhatsApp/Media/a.jpg", mtime = 10)
        val c = file(3, "/storage/emulated/0/DCIM/c.jpg", mtime = 20)
        repo.groups += exact(1, a1, a2)
        repo.groups += GroupWithMembers(2, "NEAR:x", MatchTier.NEAR, MediaKind.IMAGE, persistentListOf(a1, c), keepChoice = null)

        listOf(KeepRule.Best, KeepRule.Newest, KeepRule.Oldest, KeepRule.Largest).forEach { rule ->
            val selected = SelectAllButKeepersUseCase(repo)(GroupFilter(tier = MatchTier.NEAR), rule).map { it.fileId }.toSet()
            val nearKeeper = repo.groups[1].keeperFor(rule).fileId
            assertFalse("$rule selected the near group's keeper", nearKeeper in selected)
        }
        // With no tier chosen the selection spans every tier, and each pick carries the tier it came from.
        val everywhere = SelectAllButKeepersUseCase(repo)(GroupFilter(), KeepRule.Newest)
        assertEquals(listOf(2L, 3L), everywhere.map { it.fileId }.sorted())
        assertEquals(setOf(MatchTier.EXACT, MatchTier.NEAR), everywhere.map { it.tier }.toSet())
    }

    @Test
    fun `duplicate folders are listed with the most copies first`() = runTest {
        repo.groups += exact(1, file(1, "/storage/emulated/0/DCIM/a.jpg"), file(2, "/storage/emulated/0/Download/a.jpg"))
        repo.groups += exact(2, file(3, "/storage/emulated/0/Download/b.jpg"), file(4, "/storage/emulated/0/Download/c.jpg"))

        assertEquals(
            listOf(DuplicateFolder("/storage/emulated/0/Download", 3), DuplicateFolder("/storage/emulated/0/DCIM", 1)),
            GetDuplicateFoldersUseCase(repo)(GroupFilter()),
        )
    }
}
