package com.vipuljha.dupedigger.presentation.results

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.GroupFacet
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.KeepRule
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.StorageUsage
import com.vipuljha.dupedigger.domain.model.TrashConsent
import com.vipuljha.dupedigger.domain.repository.DeviceStorage
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.usecase.ConfirmTrashUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateFoldersUseCase
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateGroupsUseCase
import com.vipuljha.dupedigger.domain.usecase.SelectAllButKeepersUseCase
import com.vipuljha.dupedigger.domain.usecase.UndoRemovalUseCase
import com.vipuljha.dupedigger.presentation.quarantine.restoreMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Results: saved groups by kind and tier, most space to free first, with selection of the files that could go.
 * - Both chip rows are counted from the same facets, so a count always matches what the other chip leaves.
 * - A selection outlives list refreshes: when the saved groups change it is pruned to the files that can still go,
 *   instead of being dropped, and only a new scan clears it outright.
 * - "Select all" covers every matching group, not only the loaded pages, and every tier the filter shows.
 * - Delete runs after a confirmation that names the space it frees; media the app may not trash directly wait for the
 *   system prompt, and Undo puts back what was trashed directly or quarantined.
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val groupRepository: DuplicateGroupRepository,
    scanRuns: ScanRunRepository,
    getGroups: GetDuplicateGroupsUseCase,
    private val selectAllButKeepers: SelectAllButKeepersUseCase,
    private val getDuplicateFolders: GetDuplicateFoldersUseCase,
    private val deleteDuplicates: DeleteDuplicatesUseCase,
    private val confirmTrash: ConfirmTrashUseCase,
    private val undoRemoval: UndoRemovalUseCase,
    private val deviceStorage: DeviceStorage,
) : MviViewModel<ResultsState, ResultsIntent, ResultsEffect>(ResultsState()) {

    private var pendingConsent: Pair<TrashConsent, Long>? = null
    private var facets: List<GroupFacet> = emptyList()
    private var storage: StorageUsage? = null

    private val filter: Flow<GroupFilter> =
        state.map { GroupFilter(kind = it.kind, tier = it.tier) }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: Flow<PagingData<GroupRowUi>> = filter
        .flatMapLatest { getGroups(it) }
        .map { page -> page.map { it.toRow() } }
        .cachedIn(viewModelScope)
        .combine(state.map { it.selection.keys }.distinctUntilChanged()) { page, selected ->
            page.map { row ->
                val picked = row.removable.keys.count { it in selected }
                row.copy(
                    selected = picked == row.removable.size,
                    partlySelected = picked in 1 until row.removable.size
                )
            }
        }

    init {
        groupRepository.observeFacets()
            .onEach { facets = it; refreshFacets() }
            .launchIn(viewModelScope)
        scanRuns.observeLatest()
            .map { it?.id }
            .distinctUntilChanged()
            .drop(1)
            .onEach { select(emptyMap()) }
            .launchIn(viewModelScope)
        // Deletes, keep choices and "not duplicates" all change which files can still go; a stale id fails to delete.
        groupRepository.observeDecisionChanges()
            .onEach { pruneSelection() }
            .launchIn(viewModelScope)
        viewModelScope.launch { readStorage() }
    }

    override fun handleIntent(intent: ResultsIntent) {
        when (intent) {
            is ResultsIntent.KindSelected -> if (intent.kind != currentState.kind) {
                setState { copy(kind = intent.kind) }
                refreshFacets()
                select(emptyMap())
            }

            is ResultsIntent.GroupToggled -> {
                val row = intent.row
                val selection = currentState.selection
                select(
                    if (row.selected) {
                        selection - row.removable.keys
                    } else {
                        selection + row.removable.mapValues { (_, size) ->
                            SelectedUi(
                                size,
                                row.tier
                            )
                        }
                    },
                )
            }

            is ResultsIntent.TierSelected -> if (intent.tier != currentState.tier) {
                setState { copy(tier = intent.tier) }
                refreshFacets()
                select(emptyMap())
            }

            ResultsIntent.ShowAllClicked -> {
                setState { copy(kind = null, tier = null) }
                refreshFacets()
                select(emptyMap())
            }

            ResultsIntent.SelectAllToggled -> if (currentState.allSelected) {
                select(emptyMap())
            } else {
                selectAll(
                    GroupFilter(currentState.kind, currentState.tier),
                    currentState.keepRule ?: KeepRule.Best
                )
            }

            is ResultsIntent.KeepRuleSelected -> {
                setState { copy(folderChoices = null) }
                selectAll(GroupFilter(currentState.kind, currentState.tier), intent.rule)
            }

            ResultsIntent.KeepFromFolderClicked -> viewModelScope.launch {
                val folders = getDuplicateFolders(GroupFilter(currentState.kind, currentState.tier))
                setState { copy(folderChoices = folders.map { it.toChoice() }.toImmutableList()) }
            }

            ResultsIntent.FolderPickerDismissed -> setState { copy(folderChoices = null) }
            ResultsIntent.ClearSelection -> select(emptyMap())
            ResultsIntent.StartScanClicked -> sendEffect(ResultsEffect.OpenScan)
            ResultsIntent.DeleteClicked -> if (currentState.hasSelection && !currentState.deleting) {
                setState {
                    copy(
                        confirmDelete = deleteConfirm(
                            selection,
                            selection.values.count { it.tier != MatchTier.EXACT },
                            storage
                        )
                    )
                }
            }

            ResultsIntent.DeleteDismissed -> setState { copy(confirmDelete = null) }
            ResultsIntent.DeleteConfirmed -> delete()
            is ResultsIntent.TrashAnswered -> trashAnswered(intent.accepted)
            is ResultsIntent.UndoClicked -> undo(intent.undo)
        }
    }

    private fun delete() {
        if (currentState.deleting) return
        val selection = currentState.selection
        setState { copy(confirmDelete = null, deleting = true, deleteProgress = 0f) }
        viewModelScope.launch {
            val outcome = deleteDuplicates(selection.keys) { done, total ->
                setState { copy(deleteProgress = if (total == 0) 1f else done.toFloat() / total) }
            }
            when (outcome) {
                is Outcome.Failure -> sendEffect(ResultsEffect.ShowMessage(outcome.error.toUiText()))
                is Outcome.Success -> {
                    val result = outcome.data
                    select(emptyMap())
                    removedMessage(
                        result.removedIds.size,
                        result.removedIds.sumOf { selection[it]?.size ?: 0L })?.let {
                        sendEffect(
                            ResultsEffect.ShowMessage(
                                it,
                                UndoUi(
                                    result.trashedUris,
                                    result.quarantineIds
                                ).takeIf { undo -> undo.count > 0 })
                        )
                    }
                    failedMessage(result.failedIds.size)?.let {
                        sendEffect(
                            ResultsEffect.ShowMessage(
                                it
                            )
                        )
                    }
                    result.consent?.let { consent ->
                        pendingConsent =
                            consent to consent.fileIds.sumOf { selection[it]?.size ?: 0L }
                        sendEffect(ResultsEffect.LaunchTrash(consent.platformRequest))
                    }
                }
            }
            setState { copy(deleting = false, deleteProgress = 0f) }
            readStorage()
        }
    }

    private fun trashAnswered(accepted: Boolean) {
        val (consent, bytes) = pendingConsent ?: return
        pendingConsent = null
        if (!accepted) return
        viewModelScope.launch {
            confirmTrash(consent)
            removedMessage(consent.fileIds.size, bytes)?.let {
                sendEffect(
                    ResultsEffect.ShowMessage(
                        it
                    )
                )
            }
            readStorage()
        }
    }

    private fun undo(undo: UndoUi) {
        viewModelScope.launch {
            val failed = undoRemoval(undo.trashedUris, undo.quarantineIds)
            sendEffect(ResultsEffect.ShowMessage(restoreMessage(undo.count - failed, failed)))
            readStorage()
        }
    }

    private fun selectAll(filter: GroupFilter, rule: KeepRule) {
        if (currentState.selecting) return
        setState { copy(selecting = true) }
        viewModelScope.launch {
            val files = selectAllButKeepers(filter, rule)
            setState { copy(selecting = false) }
            if (GroupFilter(currentState.kind, currentState.tier) == filter) {
                select(
                    files.associate { it.fileId to SelectedUi(it.size, it.tier) },
                    rule,
                    everything = true
                )
            }
        }
    }

    /** Drops files that have left the saved groups or became a keeper, so a delete never asks for one it mustn't take. */
    private fun pruneSelection() {
        if (currentState.selection.isEmpty()) return
        viewModelScope.launch {
            val removable = groupRepository.stillRemovable(currentState.selection.keys)
            val kept = currentState.selection.filterKeys { it in removable }
            if (kept.size != currentState.selection.size) select(
                kept,
                currentState.keepRule,
                currentState.selectedEverything
            )
        }
    }

    private fun refreshFacets() = setState {
        copy(
            kindChips = kindChips(facets, tier, kind),
            tierChips = tierChips(facets, kind, tier),
            selectable = selectableUi(facets, kind, tier),
        )
    }

    private suspend fun readStorage() {
        storage = deviceStorage.usage()
    }

    private fun select(
        selection: Map<Long, SelectedUi>,
        rule: KeepRule? = null,
        everything: Boolean = false
    ) = setState {
        copy(
            selection = if (selection.isEmpty()) persistentMapOf() else selection.toImmutableMap(),
            selectionSummary = selectionSummary(selection),
            keepRule = rule,
            selectedEverything = everything && selection.isNotEmpty(),
        )
    }
}
