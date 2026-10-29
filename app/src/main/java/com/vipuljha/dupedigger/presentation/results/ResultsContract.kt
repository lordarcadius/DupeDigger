package com.vipuljha.dupedigger.presentation.results

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** [selection] maps each selected file id to its size; it is cleared when the filter changes or a new scan starts. */
@Immutable
data class ResultsState(
    val kind: MediaKind? = null,
    val kindChips: ImmutableList<KindChipUi> = persistentListOf(),
    val selection: ImmutableMap<Long, Long> = persistentMapOf(),
    val selectionSummary: UiText? = null,
    val selecting: Boolean = false,
) : UiState

@Immutable
data class KindChipUi(val kind: MediaKind?, val label: UiText)

/** One group: its keeper's name, its copies and tier, and the files that could go ([removable] maps id to size). */
@Immutable
data class GroupRowUi(
    val groupId: Long,
    val kind: MediaKind,
    val title: String,
    val detail: UiText,
    val reclaim: UiText,
    val removable: ImmutableMap<Long, Long>,
    val selected: Boolean = false,
)

sealed interface ResultsIntent : UiIntent {
    data class KindSelected(val kind: MediaKind?) : ResultsIntent
    data class GroupToggled(val row: GroupRowUi) : ResultsIntent
    data object SelectAllButKeepers : ResultsIntent
    data object ClearSelection : ResultsIntent
    data object StartScanClicked : ResultsIntent
}

sealed interface ResultsEffect : UiEffect {
    data object OpenScan : ResultsEffect
}
