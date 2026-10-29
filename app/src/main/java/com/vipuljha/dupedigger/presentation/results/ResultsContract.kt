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
    val confirmDelete: DeleteConfirmUi? = null,
    val deleting: Boolean = false,
) : UiState

/** The delete confirmation: how many files and how much space. */
@Immutable
data class DeleteConfirmUi(val count: Int, val title: UiText, val body: UiText)

@Immutable
data class KindChipUi(val kind: MediaKind?, val label: UiText)

/** One group: its keeper's name, its copies and tier, and the files that could go ([removable] maps id to size). */
@Immutable
data class GroupRowUi(
    val groupId: Long,
    val fingerprint: String,
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
    data object DeleteClicked : ResultsIntent
    data object DeleteConfirmed : ResultsIntent
    data object DeleteDismissed : ResultsIntent
    data class TrashAnswered(val accepted: Boolean) : ResultsIntent
    data class UndoClicked(val undo: UndoUi) : ResultsIntent
}

sealed interface ResultsEffect : UiEffect {
    data object OpenScan : ResultsEffect

    /** Show the system trash prompt; [request] is the platform's `IntentSender`. */
    data class LaunchTrash(val request: Any) : ResultsEffect

    /** A snackbar; with [undo] it offers Undo. */
    data class ShowMessage(val message: UiText, val undo: UndoUi? = null) : ResultsEffect
}

/** What Undo puts back: media this app trashed, and quarantined files. */
@Immutable
data class UndoUi(val trashedUris: ImmutableList<String>, val quarantineIds: ImmutableList<Long>) {
    val count: Int get() = trashedUris.size + quarantineIds.size
}
