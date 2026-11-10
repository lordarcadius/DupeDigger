package com.vipuljha.dupedigger.presentation.results

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.KeepRule
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * [selection] maps each selected file id to its size; it is pruned to what the filter still shows, and dropped when
 * a new scan starts.
 * [keepRule] is the rule the current selection was made with, null once the selection is changed by hand.
 * [folderChoices] is set while the user picks the folder whose copies to keep.
 * [selectable] is what "Select all" would pick under the current filter, known before anything is loaded.
 */
@Immutable
data class ResultsState(
    val kind: MediaKind? = null,
    val kindChips: ImmutableList<KindChipUi> = persistentListOf(),
    val tier: MatchTier? = null,
    val tierChips: ImmutableList<TierChipUi> = persistentListOf(),
    val selection: ImmutableMap<Long, SelectedUi> = persistentMapOf(),
    val selectionSummary: UiText? = null,
    val selectable: SelectableUi = SelectableUi(),
    val selecting: Boolean = false,
    val keepRule: KeepRule? = null,
    /** Set while the selection is everything a bulk pick could take, so the header checkbox knows to clear it. */
    val selectedEverything: Boolean = false,
    val folderChoices: ImmutableList<FolderChoiceUi>? = null,
    val confirmDelete: DeleteConfirmUi? = null,
    val deleting: Boolean = false,
    /** How far a running delete has got, 0–1; only meaningful while [deleting]. */
    val deleteProgress: Float = 0f,
) : UiState {
    /** A row with only "All" and one entry filters nothing, so it stays hidden unless that entry is the chosen one. */
    val showKindChips: Boolean get() = kindChips.size > 2 || kind != null
    val showTierChips: Boolean get() = tierChips.size > 2 || tier != null
    val hasSelection: Boolean get() = selection.isNotEmpty()

    /**
     * True once nothing more can be picked. The count alone isn't enough: a file that keeps another group is left
     * out of a bulk selection, so a complete selection can hold fewer files than the facets count.
     */
    val allSelected: Boolean get() = hasSelection && (selectedEverything || selection.size >= selectable.files)
}

/** A selected copy: its size, and the tier of the group it came from, so the confirmation can call out similar files. */
@Immutable
data class SelectedUi(val size: Long, val tier: MatchTier)

/** How many copies the current filter offers and what they take, for the "Select all" row. */
@Immutable
data class SelectableUi(val files: Int = 0, val bytes: Long = 0L)

/**
 * The delete confirmation: how many files, how much it frees, what free space that leaves, and a warning when
 * the selection holds files that are only similar rather than byte-identical.
 */
@Immutable
data class DeleteConfirmUi(
    val count: Int,
    val title: UiText,
    val body: UiText,
    val freeAfter: UiText? = null,
    val similarWarning: UiText? = null,
)

@Immutable
data class FolderChoiceUi(val path: String, val name: String, val detail: UiText)

@Immutable
data class KindChipUi(val kind: MediaKind?, val label: UiText)

@Immutable
data class TierChipUi(val tier: MatchTier?, val label: UiText)

/** One group: its keeper's name, its copies and tier, and the files that could go ([removable] maps id to size). */
@Immutable
data class GroupRowUi(
    val groupId: Long,
    val fingerprint: String,
    val kind: MediaKind,
    val tier: MatchTier,
    val title: String,
    val detail: UiText,
    val reclaim: UiText,
    val thumbnail: String?,
    /** Marks groups that aren't byte-identical, with their similarity; null for exact groups. */
    val tierTag: UiText?,
    val removable: ImmutableMap<Long, Long>,
    val selected: Boolean = false,
    /** Set when some but not all of the group's copies are selected, which the checkbox shows differently. */
    val partlySelected: Boolean = false,
)

sealed interface ResultsIntent : UiIntent {
    data class KindSelected(val kind: MediaKind?) : ResultsIntent
    data class TierSelected(val tier: MatchTier?) : ResultsIntent
    data object ShowAllClicked : ResultsIntent
    data class GroupToggled(val row: GroupRowUi) : ResultsIntent

    /** The header checkbox: selects every copy the filter shows, or clears the selection when it is already full. */
    data object SelectAllToggled : ResultsIntent
    data class KeepRuleSelected(val rule: KeepRule) : ResultsIntent
    data object KeepFromFolderClicked : ResultsIntent
    data object FolderPickerDismissed : ResultsIntent
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
