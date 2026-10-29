package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.RemovalResult
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.model.TrashConsent
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.FileRemover
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteDuplicatesUseCaseTest {

    /** Group member sets: {1, 2, 3} and {3, 4}; file 9 is in no group. */
    private val groups = FakeGroups(listOf(setOf(1L, 2L, 3L), setOf(3L, 4L)))
    private val index = FakeIndex((1L..9L).toSet())
    private val remover = FakeRemover()
    private val delete = DeleteDuplicatesUseCase(groups, index, remover)

    @Test
    fun `deleting every member of a group is refused before anything moves`() = runTest {
        val outcome = delete(setOf(1, 2, 3))

        assertEquals(DomainError.WouldRemoveAllCopies, (outcome as Outcome.Failure).error)
        assertTrue(remover.calls.isEmpty())
        assertEquals((1L..9L).toSet(), index.ids)
    }

    @Test
    fun `a file shared with a second group can't empty that group`() = runTest {
        val outcome = delete(setOf(3, 4))

        assertEquals(DomainError.WouldRemoveAllCopies, (outcome as Outcome.Failure).error)
        assertTrue(remover.calls.isEmpty())
    }

    @Test
    fun `a file in no saved group is refused`() = runTest {
        val outcome = delete(setOf(1, 9))

        assertEquals(DomainError.NotFound, (outcome as Outcome.Failure).error)
        assertTrue(remover.calls.isEmpty())
    }

    @Test
    fun `removed files leave the index and failed ones stay`() = runTest {
        remover.fail += 2L

        val result = (delete(setOf(1, 2)) as Outcome.Success).data

        assertEquals(listOf(1L), result.removedIds)
        assertEquals(listOf(2L), result.failedIds)
        assertEquals(listOf(listOf(1L, 2L)), remover.calls)
        assertEquals((2L..9L).toSet(), index.ids)
    }

    @Test
    fun `media awaiting consent stays indexed until the user agrees`() = runTest {
        remover.needConsent += 1L

        val result = (delete(setOf(1, 2)) as Outcome.Success).data

        assertEquals(listOf(2L), result.removedIds)
        assertEquals(listOf(1L), result.consent!!.fileIds)
        assertTrue(1L in index.ids)
        ConfirmTrashUseCase(index)(result.consent!!)
        assertTrue(1L !in index.ids)
    }

    private class FakeGroups(private val memberSets: List<Set<Long>>) : DuplicateGroupRepository {
        override suspend fun groupsContaining(fileIds: Set<Long>) = memberSets.filter { set -> set.any { it in fileIds } }
        override suspend fun ignoredFingerprints() = emptySet<String>()
        override suspend fun replaceGroups(scanId: Long, groups: List<FoundGroup>) = Unit
        override suspend fun keepChoice(fingerprint: String): String? = null
    }

    private class FakeIndex(initial: Set<Long>) : FileIndexRepository {
        val ids = initial.toMutableSet()

        override suspend fun filesByIds(ids: Collection<Long>) =
            ids.filter { it in this.ids }.map { StoredFile(it, "file:/f$it", "/f$it", "f$it", MediaKind.IMAGE, 10) }
        override suspend fun remove(ids: Collection<Long>) {
            this.ids -= ids.toSet()
        }
        override suspend fun record(files: List<DiscoveredFile>, scanId: Long) = IndexStats()
        override suspend fun removeNotSeen(scope: ScanScope, scanId: Long) = 0
        override suspend fun filesSeenIn(scanId: Long) = emptyList<IndexedFile>()
        override suspend fun saveHashes(files: List<IndexedFile>) = Unit
    }

    private class FakeRemover : FileRemover {
        val calls = mutableListOf<List<Long>>()
        val fail = mutableSetOf<Long>()
        val needConsent = mutableSetOf<Long>()

        override suspend fun remove(files: List<StoredFile>): RemovalResult {
            calls += files.map { it.id }
            val ids = files.map { it.id }
            val consentIds = ids.filter { it in needConsent }
            return RemovalResult(
                removedIds = ids.filter { it !in fail && it !in needConsent }.toImmutableList(),
                trashedUris = persistentListOf(),
                quarantineIds = persistentListOf(),
                consent = consentIds.takeIf { it.isNotEmpty() }
                    ?.let { TrashConsent(it.toImmutableList(), persistentListOf(), platformRequest = Unit) },
                failedIds = ids.filter { it in fail }.toImmutableList(),
            )
        }
    }
}
