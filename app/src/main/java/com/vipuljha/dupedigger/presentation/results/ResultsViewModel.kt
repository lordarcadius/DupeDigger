package com.vipuljha.dupedigger.presentation.results

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.TrashConsent
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.usecase.ConfirmTrashUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateGroupsUseCase
import com.vipuljha.dupedigger.domain.usecase.SelectAllButKeepersUseCase
import com.vipuljha.dupedigger.domain.usecase.UndoRemovalUseCase
import com.vipuljha.dupedigger.presentation.quarantine.restoreMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentMapOf
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
 * Results: saved groups by kind, most space to free first, with selection of the files that could go.
 * - Selection is cleared when the filter changes, a new scan starts, or a keep choice or group changes, so it never
 *   holds files the user can't see, that a rescan regrouped, or that are now a group's keeper.
 * - "Select all but keepers" covers every matching group, not only the loaded pages.
 * - Delete runs after a confirmation; media the app may not trash directly wait for the system prompt, and
 *   Undo puts back what was trashed directly or quarantined.
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    groupRepository: DuplicateGroupRepository,
    scanRuns: ScanRunRepository,
    getGroups: GetDuplicateGroupsUseCase,
    private val selectAllButKeepers: SelectAllButKeepersUseCase,
    private val deleteDuplicates: DeleteDuplicatesUseCase,
    private val confirmTrash: ConfirmTrashUseCase,
    private val undoRemoval: UndoRemovalUseCase,
) : MviViewModel<ResultsState, ResultsIntent, ResultsEffect>(ResultsState()) {

    private var pendingConsent: Pair<TrashConsent, Long>? = null

    private val filter: Flow<GroupFilter> = state.map { GroupFilter(kind = it.kind) }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: Flow<PagingData<GroupRowUi>> = filter
        .flatMapLatest { getGroups(it) }
        .map { page -> page.map { it.toRow() } }
        .cachedIn(viewModelScope)
        .combine(state.map { it.selection.keys }.distinctUntilChanged()) { page, selected ->
            page.map { row -> row.copy(selected = row.removable.keys.all { it in selected }) }
        }

    init {
        groupRepository.observeReclaimable()
            .onEach { kinds -> setState { copy(kindChips = kindChips(kinds)) } }
            .launchIn(viewModelScope)
        scanRuns.observeLatest()
            .map { it?.id }
            .distinctUntilChanged()
            .drop(1)
            .onEach { select(emptyMap()) }
            .launchIn(viewModelScope)
        // A new keeper or a "not duplicates" may touch selected files; delete must never take a chosen keeper.
        groupRepository.observeDecisionChanges()
            .onEach { select(emptyMap()) }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: ResultsIntent) {
        when (intent) {
            is ResultsIntent.KindSelected -> if (intent.kind != currentState.kind) {
                setState { copy(kind = intent.kind) }
                select(emptyMap())
            }
            is ResultsIntent.GroupToggled -> {
                val row = intent.row
                select(if (row.selected) currentState.selection - row.removable.keys else currentState.selection + row.removable)
            }
            ResultsIntent.SelectAllButKeepers -> selectAll(currentState.kind)
            ResultsIntent.ClearSelection -> select(emptyMap())
            ResultsIntent.StartScanClicked -> sendEffect(ResultsEffect.OpenScan)
            ResultsIntent.DeleteClicked -> if (currentState.selection.isNotEmpty() && !currentState.deleting) {
                setState { copy(confirmDelete = deleteConfirm(selection)) }
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
        setState { copy(confirmDelete = null, deleting = true) }
        viewModelScope.launch {
            when (val outcome = deleteDuplicates(selection.keys)) {
                is Outcome.Failure -> sendEffect(ResultsEffect.ShowMessage(outcome.error.toUiText()))
                is Outcome.Success -> {
                    val result = outcome.data
                    select(emptyMap())
                    removedMessage(result.removedIds.size, result.removedIds.sumOf { selection[it] ?: 0L })?.let {
                        sendEffect(ResultsEffect.ShowMessage(it, UndoUi(result.trashedUris, result.quarantineIds).takeIf { undo -> undo.count > 0 }))
                    }
                    failedMessage(result.failedIds.size)?.let { sendEffect(ResultsEffect.ShowMessage(it)) }
                    result.consent?.let { consent ->
                        pendingConsent = consent to consent.fileIds.sumOf { selection[it] ?: 0L }
                        sendEffect(ResultsEffect.LaunchTrash(consent.platformRequest))
                    }
                }
            }
            setState { copy(deleting = false) }
        }
    }

    private fun trashAnswered(accepted: Boolean) {
        val (consent, bytes) = pendingConsent ?: return
        pendingConsent = null
        if (!accepted) return
        viewModelScope.launch {
            confirmTrash(consent)
            removedMessage(consent.fileIds.size, bytes)?.let { sendEffect(ResultsEffect.ShowMessage(it)) }
        }
    }

    private fun undo(undo: UndoUi) {
        viewModelScope.launch {
            val failed = undoRemoval(undo.trashedUris, undo.quarantineIds)
            sendEffect(ResultsEffect.ShowMessage(restoreMessage(undo.count - failed, failed)))
        }
    }

    private fun selectAll(kind: MediaKind?) {
        if (currentState.selecting) return
        setState { copy(selecting = true) }
        viewModelScope.launch {
            val files = selectAllButKeepers(GroupFilter(kind = kind))
            setState { copy(selecting = false) }
            if (currentState.kind == kind) select(files.associate { it.fileId to it.size })
        }
    }

    private fun select(selection: Map<Long, Long>) = setState {
        copy(
            selection = if (selection.isEmpty()) persistentMapOf() else selection.toImmutableMap(),
            selectionSummary = selectionSummary(selection),
        )
    }
}
