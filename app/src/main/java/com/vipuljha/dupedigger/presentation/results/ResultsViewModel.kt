package com.vipuljha.dupedigger.presentation.results

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.domain.model.GroupFilter
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.usecase.GetDuplicateGroupsUseCase
import com.vipuljha.dupedigger.domain.usecase.SelectAllButKeepersUseCase
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
 * - Selection is cleared when the filter changes or a new scan starts, so it never holds files the user can't see
 *   or that a rescan regrouped.
 * - "Select all but keepers" covers every matching group, not only the loaded pages.
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    groupRepository: DuplicateGroupRepository,
    scanRuns: ScanRunRepository,
    getGroups: GetDuplicateGroupsUseCase,
    private val selectAllButKeepers: SelectAllButKeepersUseCase,
) : MviViewModel<ResultsState, ResultsIntent, ResultsEffect>(ResultsState()) {

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
