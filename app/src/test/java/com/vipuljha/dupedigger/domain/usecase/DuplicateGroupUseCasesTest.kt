package com.vipuljha.dupedigger.domain.usecase

import androidx.paging.PagingData
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.GroupWithMembers
import com.vipuljha.dupedigger.domain.model.ExactGroup
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.exactFingerprint
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DuplicateGroupUseCasesTest {

    private val repo = FakeGroups()

    class FakeGroups : DuplicateGroupRepository {
        val ignored = mutableSetOf<String>()
        val keepChoices = mutableMapOf<String, String>()
        var saved: Pair<Long, List<FoundGroup>>? = null

        override suspend fun ignoredFingerprints() = ignored.toSet()
        override suspend fun replaceGroups(scanId: Long, groups: List<FoundGroup>) {
            saved = scanId to groups
        }
        override suspend fun keepChoice(fingerprint: String) = keepChoices[fingerprint]
        override suspend fun groupsContaining(fileIds: Set<Long>) = emptyList<Set<Long>>()
        override fun observeReclaimable() = emptyFlow<List<KindReclaim>>()
        override fun pagedGroups(filter: GroupFilter) = emptyFlow<PagingData<GroupWithMembers>>()
        override suspend fun allGroups(filter: GroupFilter) = emptyList<GroupWithMembers>()
        override fun observeGroup(fingerprint: String) = emptyFlow<GroupWithMembers?>()
        override fun observeDecisionChanges() = emptyFlow<Unit>()
        override suspend fun setKeepChoice(fingerprint: String, sourceUri: String) = Unit
        override suspend fun markNotDuplicates(fingerprint: String) = Unit
    }

    @Test
    fun `the fingerprint follows the content, not the files holding it`() {
        assertEquals(exactFingerprint(10, "abc"), exactFingerprint(10, "abc"))
        assertNotEquals(exactFingerprint(10, "abc"), exactFingerprint(10, "abd"))
        assertNotEquals(exactFingerprint(10, "abc"), exactFingerprint(11, "abc"))
        assertEquals("EXACT:10:abc", exactFingerprint(10, "abc"))
    }

    @Test
    fun `groups marked not duplicates are left out, even with a new copy`() = runTest {
        repo.ignored += exactFingerprint(100, "aaa")
        val groups = listOf(
            ExactGroup(100, "aaa", persistentListOf(1, 2, 3)),
            ExactGroup(50, "bbb", persistentListOf(4, 5)),
        )

        val kept = SaveExactGroupsUseCase(repo)(scanId = 7, exactGroups = groups)

        assertEquals(listOf(groups[1]), kept)
        val (scanId, saved) = repo.saved!!
        assertEquals(7L, scanId)
        assertEquals(listOf(FoundGroup("EXACT:50:bbb", MatchTier.EXACT, 1f, persistentListOf(4, 5))), saved)
    }

    @Test
    fun `nothing left still replaces the previous results`() = runTest {
        repo.ignored += exactFingerprint(100, "aaa")

        SaveExactGroupsUseCase(repo)(scanId = 8, exactGroups = listOf(ExactGroup(100, "aaa", persistentListOf(1, 2))))

        assertEquals(8L to emptyList<FoundGroup>(), repo.saved)
    }

    private fun member(id: Long, path: String, mtime: Long) =
        GroupMember(id, "file:$path", path, size = 10, mtime = mtime)

    @Test
    fun `a saved keep choice wins while its file is still in the group`() = runTest {
        val camera = member(1, "/storage/emulated/0/DCIM/a.jpg", 10)
        val copy = member(2, "/storage/emulated/0/Download/a.jpg", 20)
        val suggest = SuggestKeeperUseCase(repo)

        assertEquals(camera, suggest("fp", listOf(copy, camera)))
        repo.keepChoices["fp"] = copy.sourceUri
        assertEquals(copy, suggest("fp", listOf(copy, camera)))
    }

    @Test
    fun `a keep choice for a file that left the group falls back to the ranking`() = runTest {
        val camera = member(1, "/storage/emulated/0/DCIM/a.jpg", 10)
        val copy = member(2, "/storage/emulated/0/Download/a.jpg", 20)
        repo.keepChoices["fp"] = "file:/storage/emulated/0/Pictures/deleted.jpg"

        assertEquals(camera, SuggestKeeperUseCase(repo)("fp", listOf(copy, camera)))
    }
}
