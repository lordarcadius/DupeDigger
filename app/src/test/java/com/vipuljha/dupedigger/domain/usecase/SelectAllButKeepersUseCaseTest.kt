package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.presentation.FakeReclaimGroups
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
