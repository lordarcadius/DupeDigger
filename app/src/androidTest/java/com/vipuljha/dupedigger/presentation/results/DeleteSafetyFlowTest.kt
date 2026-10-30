package com.vipuljha.dupedigger.presentation.results

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.entity.FileIndexEntity
import com.vipuljha.dupedigger.data.repository.DuplicateGroupRepositoryImpl
import com.vipuljha.dupedigger.data.repository.FileIndexRepositoryImpl
import com.vipuljha.dupedigger.data.repository.ScanRunRepositoryImpl
import com.vipuljha.dupedigger.domain.model.FoundGroup
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.QuarantinedFile
import com.vipuljha.dupedigger.domain.model.RemovalResult
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.repository.FileRemover
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import com.vipuljha.dupedigger.domain.usecase.ConfirmTrashUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateGroupsUseCase
import com.vipuljha.dupedigger.domain.usecase.RestoreQuarantinedUseCase
import com.vipuljha.dupedigger.domain.usecase.SelectAllButKeepersUseCase
import com.vipuljha.dupedigger.domain.usecase.UndoRemovalUseCase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * The delete flow on the real Results screen, ViewModel, use cases and an in-memory database; only file removal is faked.
 * Two exact groups of three copies each are seeded.
 */
@RunWith(AndroidJUnit4::class)
class DeleteSafetyFlowTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: DupeDiggerDatabase
    private lateinit var groupIds: List<List<Long>>
    private val remover = RecordingRemover()
    private val quarantine = RecordingQuarantine()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        val groups = DuplicateGroupRepositoryImpl(db, db.duplicateGroupDao(), db.ignoredGroupDao(), db.keepChoiceDao())
        groupIds = runBlocking {
            val ids = (0 until 2).map { g ->
                (0 until 3).map { c ->
                    db.fileIndexDao().insert(
                        FileIndexEntity(
                            sourceUri = "file:/storage/emulated/0/Docs/g$g/c$c.pdf", path = "/storage/emulated/0/Docs/g$g/c$c.pdf",
                            name = "report-$g-$c.pdf", mimeType = "application/pdf", mediaKind = MediaKind.DOCUMENT.name,
                            size = 1_000_000, mtime = 1, volume = null, lastSeenScanId = 1,
                        ),
                    )
                }
            }
            groups.replaceGroups(1, ids.mapIndexed { g, members -> FoundGroup("EXACT:$g", MatchTier.EXACT, 1f, members.toImmutableList()) })
            ids
        }
        val files = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao())
        val viewModel = ResultsViewModel(
            groups, ScanRunRepositoryImpl(db, db.scanRunDao()), GetDuplicateGroupsUseCase(groups), SelectAllButKeepersUseCase(groups),
            DeleteDuplicatesUseCase(groups, files, remover), ConfirmTrashUseCase(files),
            UndoRemovalUseCase(remover, RestoreQuarantinedUseCase(quarantine)),
        )
        rule.setContent {
            DupeDiggerTheme { ResultsRoute(onMenuClick = null, openGroup = null, onOpenGroup = {}, onStartScan = {}, viewModel = viewModel) }
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun cancellingTheConfirmationRemovesNothing() {
        selectDuplicatesAndAskToDelete()

        rule.onNode(hasText(string(R.string.action_cancel)) and hasAnyAncestor(isDialog())).performClick()

        rule.onNode(hasAnyAncestor(isDialog())).assertDoesNotExist()
        rule.onNodeWithText(selectedSummary()).assertExists()
        assertTrue(remover.asked.isEmpty())
        assertEquals(6, remainingIds().size)
    }

    @Test
    fun confirmingRemovesOnlyCopiesLeavesOneFilePerGroupAndOffersUndo() {
        selectDuplicatesAndAskToDelete()

        rule.onNode(hasText(string(R.string.action_delete)) and hasAnyAncestor(isDialog())).performClick()
        rule.waitUntil(TIMEOUT) { remover.asked.size == 4 }
        rule.waitUntil(TIMEOUT) { remainingIds().size == 2 }

        val remaining = remainingIds()
        groupIds.forEach { members -> assertEquals("one file left in $members", 1, members.count { it in remaining }) }
        assertTrue(remover.asked.none { it in remaining })

        rule.waitUntil(TIMEOUT) { rule.onAllNodes(hasText(string(R.string.action_undo))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(string(R.string.action_undo)).performClick()
        rule.waitUntil(TIMEOUT) { quarantine.restored.size == 4 }
        assertEquals(remover.asked.map { it + QUARANTINE_OFFSET }.toSet(), quarantine.restored.toSet())
    }

    /** Selects every copy but each group's keeper, then opens the confirmation and checks it names 4 files and their size. */
    private fun selectDuplicatesAndAskToDelete() {
        rule.waitUntil(TIMEOUT) { rule.onAllNodes(hasText("report-", substring = true)).fetchSemanticsNodes().size >= 2 }
        rule.onNodeWithText(string(R.string.results_action_select_duplicates)).performClick()
        rule.waitUntil(TIMEOUT) { rule.onAllNodes(hasText(selectedSummary())).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText(string(R.string.action_delete)).performClick()

        rule.onNode(hasText(context.resources.getQuantityString(R.plurals.results_delete_title, 4, "4")) and hasAnyAncestor(isDialog()))
            .assertExists()
        rule.onNode(hasText(fourMegabytes(), substring = true) and hasAnyAncestor(isDialog())).assertExists()
    }

    private fun selectedSummary() = context.resources.getQuantityString(R.plurals.results_selected, 4, "4", fourMegabytes())

    private fun fourMegabytes() = formatBytes(4_000_000).asString(context)

    private fun string(id: Int) = context.getString(id)

    private fun remainingIds(): Set<Long> = runBlocking { db.fileIndexDao().findByIds(groupIds.flatten()).map { it.id }.toSet() }

    /** Quarantines every file it's given as id + [QUARANTINE_OFFSET], so undo can be checked. */
    private class RecordingRemover : FileRemover {
        val asked: MutableList<Long> = Collections.synchronizedList(mutableListOf())

        override suspend fun remove(files: List<StoredFile>): RemovalResult {
            val ids = files.map { it.id }
            asked += ids
            return RemovalResult(
                removedIds = ids.toImmutableList(),
                trashedUris = persistentListOf(),
                quarantineIds = ids.map { it + QUARANTINE_OFFSET }.toImmutableList(),
                consent = null,
                failedIds = persistentListOf(),
            )
        }

        override suspend fun untrash(mediaUris: List<String>) = 0
    }

    private class RecordingQuarantine : QuarantineRepository {
        val restored: MutableList<Long> = Collections.synchronizedList(mutableListOf())

        override fun observeAll() = MutableStateFlow(emptyList<QuarantinedFile>())
        override suspend fun restore(id: Long): Outcome<Unit> {
            restored += id
            return Outcome.Success(Unit)
        }
        override suspend fun deletePermanently(id: Long): Outcome<Unit> = Outcome.Success(Unit)
    }

    private companion object {
        const val TIMEOUT = 5_000L
        const val QUARANTINE_OFFSET = 1_000L
    }
}
